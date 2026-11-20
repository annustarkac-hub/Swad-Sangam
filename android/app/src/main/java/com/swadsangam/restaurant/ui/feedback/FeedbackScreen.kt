package com.swadsangam.restaurant.ui.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.data.remote.FeedbackRequest
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(savedState: SavedStateHandle, private val orders: OrderRepository) : ViewModel() {
    val token: String? = savedState.get<String>("token")?.takeIf { it.isNotBlank() }
    suspend fun submit(r: FeedbackRequest) = orders.submitFeedback(r.copy(orderToken = token))
}

/** Private feedback — seen only by restaurant management unless the customer consents (FR-12). */
@Composable
fun FeedbackScreen(onDone: () -> Unit, vm: FeedbackViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    var overall by remember { mutableIntStateOf(0) }
    var food by remember { mutableIntStateOf(0) }
    var accuracy by remember { mutableIntStateOf(0) }
    var packaging by remember { mutableIntStateOf(0) }
    var staff by remember { mutableIntStateOf(0) }
    var comments by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (done) {
        EmptyState(stringResource(R.string.feedback_thanks), stringResource(R.string.feedback_thanks_body), stringResource(R.string.ok), onDone)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.feedback_intro), style = MaterialTheme.typography.bodyMedium)
        Stars(stringResource(R.string.fb_overall), overall) { overall = it }
        Stars(stringResource(R.string.fb_food), food) { food = it }
        Stars(stringResource(R.string.fb_accuracy), accuracy) { accuracy = it }
        Stars(stringResource(R.string.fb_packaging), packaging) { packaging = it }
        Stars(stringResource(R.string.fb_staff), staff) { staff = it }
        OutlinedTextField(comments, { comments = it.take(1000) }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.fb_comments)) })
        Row(Modifier.fillMaxWidth().clickable { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(consent, { consent = it })
            Text(stringResource(R.string.fb_consent), style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = overall > 0 && !sending, modifier = Modifier.fillMaxWidth(),
            onClick = {
                sending = true
                scope.launch {
                    vm.submit(FeedbackRequest(overall = overall, foodQuality = food.takeIf { it > 0 }, orderAccuracy = accuracy.takeIf { it > 0 },
                        packaging = packaging.takeIf { it > 0 }, staffBehaviour = staff.takeIf { it > 0 },
                        comments = comments.ifBlank { null }, consentToPublish = consent))
                        .onSuccess { done = true }.onFailure { error = it.message }
                    sending = false
                }
            },
        ) { Text(stringResource(R.string.send_feedback)) }
    }
}

@Composable
private fun Stars(label: String, value: Int, onChange: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Row {
        (1..5).forEach { i ->
            IconButton({ onChange(i) }) {
                Icon(if (i <= value) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.stars_n, i), tint = Color(0xFFE8A13B))
            }
        }
    }
}

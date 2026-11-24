package com.swadsangam.restaurant.ui.feedback

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.AppTheme
import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.StatusBanner
import com.swadsangam.restaurant.ui.components.BottomActionBar
import com.swadsangam.restaurant.ui.components.AppTextField
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import com.swadsangam.restaurant.ui.theme.Spacing
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
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Text(stringResource(R.string.feedback_intro), style = MaterialTheme.typography.bodyLarge, color = secondaryText)
            AppCard {
                Stars(stringResource(R.string.fb_overall), overall, big = true) { overall = it }
            }
            AppCard {
                Stars(stringResource(R.string.fb_food), food) { food = it }
                Stars(stringResource(R.string.fb_accuracy), accuracy) { accuracy = it }
                Stars(stringResource(R.string.fb_packaging), packaging) { packaging = it }
                Stars(stringResource(R.string.fb_staff), staff) { staff = it }
            }
            AppTextField(comments, { comments = it.take(1000) }, stringResource(R.string.fb_comments), singleLine = false, minLines = 3)
            Row(Modifier.fillMaxWidth().heightIn(min = Size.touch).clickable { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it })
                Text(stringResource(R.string.fb_consent), style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { StatusBanner(it, tone = Tone.Error) }
        }
        BottomActionBar {
            AppButton(stringResource(R.string.send_feedback), {
                sending = true
                scope.launch {
                    vm.submit(FeedbackRequest(overall = overall, foodQuality = food.takeIf { it > 0 }, orderAccuracy = accuracy.takeIf { it > 0 },
                        packaging = packaging.takeIf { it > 0 }, staffBehaviour = staff.takeIf { it > 0 },
                        comments = comments.ifBlank { null }, consentToPublish = consent))
                        .onSuccess { done = true }.onFailure { error = it.message }
                    sending = false
                }
            }, Modifier.fillMaxWidth(), large = true, enabled = overall > 0, loading = sending)
        }
    }
}

/** 1–5 star picker. Each star is a 48 dp target and announces its value and selection. */
@Composable
private fun Stars(label: String, value: Int, big: Boolean = false, onChange: (Int) -> Unit) {
    Text(label, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.s))
    Row {
        (1..5).forEach { i ->
            IconButton({ onChange(i) }, Modifier.size(Size.touch).semantics { selected = i <= value }) {
                Icon(if (i <= value) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.stars_n, i),
                    tint = if (i <= value) AppTheme.colors.rating else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(if (big) IconSize.l + Spacing.s else IconSize.l))
            }
        }
    }
}

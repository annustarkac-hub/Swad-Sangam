# मालिक के लिए बाकी काम (सिर्फ़ आप कर सकते हैं)

हर काम के आगे लिखा है कि कहाँ जाना है। ऊपर से नीचे क्रम में करें।

## A. ऑर्डर लेना शुरू करने के लिए (सबसे ज़रूरी)
1. **एडमिन → Menu**: हर डिश का दाम, नाम, फ़ोटो देखें → **Edit → "Details verified" ✓ → Save**। (अभी 199 में से 0 verified)
2. **एडमिन → Settings**:
   - **Opening hours** सही करें → **"Opening hours confirmed by owner" ✓**
   - **FSSAI नंबर** (14 अंक), GST रजिस्टर्ड हों तो **GSTIN** और **Tax rate** (CA से पूछकर)
   - **Delivery fee (₹)**, डिलीवरी एरिया का नोट, **WhatsApp नंबर**
   - UPI से पैसा लेना हो तो **UPI ID** और "UPI at restaurant" ✓
   - **Pickup / Delivery / Dine-in** में से जो देना है उसे ✓ करें → **Save changes**
3. **एडमिन → Tables & bookings**: टेबल जोड़ें (T1, T2…) → **QR codes** पेज से प्रिंट करके टेबल पर लगाएँ।
4. **एडमिन → Dashboard → "Start accepting app orders"**।
5. एक **टेस्ट ऑर्डर** अपने फ़ोन से दें → एडमिन में Accept → Ready → Completed करके देखें।

## B. ग्राहक खाते (Login) के लिए
6. **Supabase.com → अपना प्रोजेक्ट → Authentication → Sign In / Providers → Email → "Confirm email" OFF → Save**।
   (ON रहने पर घंटे में सिर्फ़ ~2 नए ग्राहक खाता बना पाएँगे।)

## C. Android ऐप (APK) के लिए — एक बार
7. **GitHub → Swad-Sangam → Settings → Secrets and variables → Actions → New repository secret**
   - Name: `ANDROID_KEYSTORE_PASSWORD` · Value: कोई लंबा पासवर्ड (कम से कम 16 अक्षर/अंक) — **काग़ज़ पर लिखकर रखें**
8. **Actions → "Create signing key (run once)" → Run workflow → YES लिखें → Run**
9. रन पूरा होने पर उसे खोलें → नीचे **signing-key** डाउनलोड करें → `release.jks.base64.txt` खोलें → **पूरा टेक्स्ट कॉपी** करें
10. फिर से **New repository secret** → Name: `ANDROID_KEYSTORE_BASE64` · Value: कॉपी किया टेक्स्ट
11. **Actions → "Release Android APK" → Run workflow**। 10–15 मिनट बाद **Releases** में तीन ऐप आएँगे:
    `swad-sangam.apk` (ग्राहक), `swad-sangam-admin.apk` (मालिक/मैनेजर), `swad-sangam-staff.apk` (कैशियर/किचन/वेटर)।
    वेबसाइट का "Android app" बटन ग्राहक वाला ऐप देता है। पहले भेजे गए टेस्ट APK हटाकर (uninstall) ये नए लगाएँ — इसके बाद हर अपडेट सीधे ऊपर इंस्टॉल होगा।
    डाउनलोड की गई फ़ाइल Google Drive में संभालकर रखें — इसके बिना आगे ऐप अपडेट नहीं होगा।

## D. रोज़ का बैकअप — एक बार
12. **Supabase → Project Settings → Database → Connection string → "Session pooler"** कॉपी करें (उसमें अपना DB पासवर्ड भरें)
    → GitHub secret `SUPABASE_DB_URL`
13. GitHub secret `BACKUP_PASSPHRASE` = कोई लंबा पासवर्ड (काग़ज़ पर लिखें; बैकअप खोलने के लिए चाहिए)
14. **Actions → "Database backup" → Run workflow** करके एक बार जाँच लें (हरा ✓)।

## E. स्टाफ़
15. हर स्टाफ़ **admin.html** पर अपना खाता बनाए → आप **एडमिन → Staff logins** में भूमिका (Cashier/Kitchen/Waiter) देकर **Activate** करें।
    सिर्फ़ जान-पहचान वालों को activate करें।

## E2. डिलीवरी (जब शुरू करें)
- डिलीवरी वाले लड़के **admin.html** पर खाता बनाएँ → आप **Staff logins** में भूमिका **DELIVERY** देकर Activate करें। उन्हें **Staff ऐप** दें।
- ऑर्डर कार्ड पर **Rider** चुनें। राइडर अपने फ़ोन पर "My deliveries" में ऑर्डर देखेगा → **Start delivery** → ग्राहक से **4 अंकों का कोड** पूछकर
  और पैसे (Cash/UPI) लेकर **Deliver** करेगा। बिना कोड और पैसे के ऑर्डर पूरा नहीं होगा।
- अब कोई भी ऑर्डर बिना भुगतान के "Completed" नहीं होगा। उधार (खाता) वाले ग्राहक के लिए मालिक/मैनेजर कारण लिखकर पूरा कर सकते हैं।
- **Settings → Delivery orders ✓** तभी करें जब डिलीवरी सच में शुरू हो।

## E3. Privacy / Terms पेज
- भर दिए गए (25 नवंबर 2026): नाम "Swad Sangam", शिकायत अधिकारी "The Owner", रिफ़ंड 7 दिन में cash/UPI से।
- बाकी: **FSSAI नंबर** मिलते ही बताएँ (terms पेज पर लिखना है); शिकायत अधिकारी के रूप में अपना नाम लिखवाना हो तो बताएँ।

## G. ईमेल (Brevo) — ग्राहक का sign-up / password reset ईमेल अटके नहीं (मुफ़्त, 300 ईमेल/दिन)
Supabase का अपना ईमेल घंटे में सिर्फ़ ~2 ईमेल भेजता है। Brevo जोड़ने से यह सीमा हट जाती है। कोड नहीं बदलना, ~10 मिनट।
1. **brevo.com → Sign up free** → अपने ईमेल से खाता बनाएँ (फ़ोन/ईमेल OTP आएगा)। Plan: **Free**।
2. **Brevo → ऊपर दाएँ अपना नाम → Senders, Domains & Dedicated IPs → Senders → Add a sender**
   → From name: `Swad Sangam` · From email: वह ईमेल जिससे ग्राहकों को मेल जाए → ईमेल में आया कोड डालकर **Verify**।
3. **Brevo → अपना नाम → SMTP & API → SMTP टैब → Generate a new SMTP key** → नाम `supabase` → **key कॉपी करें**
   (यह key सिर्फ़ एक बार दिखती है; किसी को चैट/WhatsApp पर न भेजें)। इसी पेज पर **SMTP Server**, **Port** और **Login** भी लिखे होते हैं।
4. **Supabase → अपना प्रोजेक्ट → Authentication → Emails → SMTP Settings → "Enable Custom SMTP" ON** और भरें:
   - Sender email: स्टेप 2 वाला verified ईमेल · Sender name: `Swad Sangam`
   - Host: `smtp-relay.brevo.com` · Port: `587`
   - Username: Brevo वाला **Login** (स्टेप 3 के पेज से) · Password: स्टेप 3 की **SMTP key** → **Save**
5. **Supabase → Authentication → Rate Limits → "Rate limit for sending emails"** = `60` प्रति घंटा → Save।
6. जाँच: वेबसाइट पर **Login → Forgot password** में अपना ईमेल डालें → 1–2 मिनट में मेल आना चाहिए (Spam फ़ोल्डर भी देखें)।
   - Gmail वाले पते से भेजने पर कुछ मेल Spam में जा सकते हैं। आगे अपना डोमेन (जैसे swadsangam.in, ~₹500–900/साल) लें तो Brevo में उसे जोड़ने से यह ठीक हो जाता है।
   - Brevo चालू होने के बाद ऊपर B-6 वाला "Confirm email" ON भी रख सकते हैं (सुरक्षित), क्योंकि अब ईमेल की सीमा नहीं अटकेगी।

## H. साइट बंद होने का अलर्ट (UptimeRobot) — मुफ़्त, हर 5 मिनट जाँच
वेबसाइट, एडमिन या बैकएंड बंद हुआ तो आपको तुरंत ईमेल/फ़ोन पर पता चलेगा। ~5 मिनट।
1. **uptimerobot.com → Register for FREE** → अपने ईमेल से खाता बनाएँ।
2. **+ New monitor** → तीन मॉनिटर बनाएँ (Interval: 5 minutes, Alert: अपना ईमेल ✓):

   | Monitor type | Friendly name | URL | और |
   |---|---|---|---|
   | HTTP(s) | Swad Sangam website | `https://annustarkac-hub.github.io/Swad-Sangam/` | — |
   | HTTP(s) | Swad Sangam admin | `https://annustarkac-hub.github.io/Swad-Sangam/admin.html` | — |
   | **Keyword** | Swad Sangam backend | `https://gqpyriurdbhhujtpizen.supabase.co/functions/v1/health` | Keyword: `SWAD-OK` · Alert when: **keyword not exists** |

   तीसरा मॉनिटर डेटाबेस तक पूरी जाँच करता है (जवाब में सिर्फ़ `SWAD-OK` आता है, कोई ग्राहक डेटा नहीं)। हर 5 मिनट की जाँच से बैकएंड जागता भी रहता है।
3. चाहें तो फ़ोन में **UptimeRobot ऐप** डालकर उसी खाते से login करें — बंद होने पर फ़ोन पर नोटिफ़िकेशन आएगा।
4. तीनों मॉनिटर कुछ मिनट में हरे (**Up**) दिखने चाहिए। लाल हो तो मुझे स्क्रीनशॉट भेजें।

## I. नक्शे पर डिलीवरी लोकेशन + दूरी के हिसाब से डिलीवरी चार्ज (मुफ़्त, OpenStreetMap)
ग्राहक अब वेबसाइट पर नक्शे में अपना घर पिन कर सकता है (या "मेरी अभी की लोकेशन")। Android ऐप में "मेरी अभी की लोकेशन लगाएँ" बटन है।
राइडर और एडमिन को ऑर्डर पर **"Map pin ↗"** मिलेगा, जो सीधे ग्राहक के दरवाज़े तक रास्ता खोलता है।
1. **एडमिन → Settings → "Set restaurant location on map"** → रेस्टोरेंट पर खड़े होकर **"Use my current location"** दबाएँ (या नक्शे पर दरवाज़े पर पिन रखें) → **Confirm location → Save changes**।
   इसके बिना दूरी नहीं निकलेगी और पुराना एक-जैसा डिलीवरी चार्ज ही लगेगा।
2. उसी पेज पर (सब वैकल्पिक, खाली/0 रखें तो पुराना तरीका चलता रहेगा):
   - **Delivery fee (₹)**: शुरुआती चार्ज, जैसे 20
   - **Km included in the delivery fee**: इतने किमी तक वही चार्ज, जैसे 2
   - **Extra delivery fee per km (₹)**: उसके बाद हर किमी का, जैसे 10
   - **Max delivery distance (km)**: इससे दूर ऑर्डर नहीं आएगा, जैसे 8
   - **Road distance factor**: नक्शे पर सीधी दूरी × यह संख्या। पहाड़ी घुमावदार सड़क के लिए 1.4 ठीक रहता है। (उदाहरण: सीधी 3.5 किमी × 1.4 = 4.9 किमी)
   - **"Customer must mark the delivery location on the map"** ✓ करें तो बिना पिन के डिलीवरी ऑर्डर नहीं आएगा।
     ध्यान दें: पुराने Android ऐप (आज से पहले वाला APK) में पिन बटन नहीं है। यह ✓ तभी करें जब ग्राहक नया ऐप डाल चुके हों।

## F. पक्का करें
16. बैनर पर **"Since - 2025"** लिखा है, बोर्ड पर **2026** — सही साल बताएँ।
17. 3 डिश की फ़ोटो नहीं है: **Hot Milk, Lime Soda, Onion Salad** (और Paneer Pasanda) — अपने फ़ोन से फ़ोटो लेकर **Menu → Edit → Photo** में डालें।

पहले से अपने-आप हो रहा है: बैकएंड हर 3 दिन जगाया जाता है (keepalive चालू है ✓)।

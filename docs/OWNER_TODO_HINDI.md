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

## F. पक्का करें
16. बैनर पर **"Since - 2025"** लिखा है, बोर्ड पर **2026** — सही साल बताएँ।
17. 3 डिश की फ़ोटो नहीं है: **Hot Milk, Lime Soda, Onion Salad** (और Paneer Pasanda) — अपने फ़ोन से फ़ोटो लेकर **Menu → Edit → Photo** में डालें।

पहले से अपने-आप हो रहा है: बैकएंड हर 3 दिन जगाया जाता है (keepalive चालू है ✓)।

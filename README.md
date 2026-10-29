# BYMQNSTER Stream Deck

Android telefonunu yatay dokunmatik kumanda olarak kullanıp OBS Studio’ya Wi‑Fi üzerinden komut gönderen bir uygulama.

> **İndir:** [BYMQNSTER Stream Deck v1.0 Wi‑Fi APK](distribution/BYMQNSTER-StreamDeck-v1.0-wifi-debug.apk)  
> Uygulama Android 10 (API 29) ve üzerini gerektirir. Bu APK Android Studio debug anahtarıyla imzalanmıştır; Google Play sürümü değildir.

## Uygulama görünümü

![BYMQNSTER Stream Deck temsili yatay ekran görünümü](docs/images/app-screen.svg)

## Kurulum

1. Yukarıdaki APK bağlantısını telefonda açıp GitHub’dan indir.
2. APK’ya dokun. Google Play Protect uygulamayı ilk kez gördüğünde tarama önerebilir. **Uygulamayı tara** seçeneğine basıp taramanın bitmesini bekle.
3. Bu telefonda yaptığımız testte Play Protect taramayı tamamladı ve uygulama güvenli görünüyor sonucunu verdi. Bu sonuç aşağıda ekran görüntüleriyle gösterilmiştir. Play Protect farklı bir uyarı verirse uyarıyı geçme; yüklemeyi durdurup APK’nın GitHub’daki bu depodan indirildiğini doğrula.
4. Güvenli sonucu çıktıktan sonra **Yükle**’ye bas. Kurulum bitince **Aç**’ı seç. Android tarayıcıdan yükleme izni isterse yalnızca bu yükleme için izin ver.

![Play Protect'in uygulamayı tarama önerisi](docs/images/play-protect-scan-prompt.jpg)

![Play Protect tarama yaparken](docs/images/play-protect-scanning.jpg)

![Testte Play Protect'in uygulamayı güvenli gördüğü sonuç](docs/images/play-protect-safe-result.jpg)

![Android kurulum tamamlandı; uygulamayı açma seçeneği](docs/images/android-install-complete.jpg)

## OBS ve Wi‑Fi bağlantısı

OBS WebSocket, OBS Studio 28 ve sonrasında yerleşik gelir. OBS’de **Araçlar → WebSocket Sunucusu Ayarları** menüsünü aç. (OBS arayüzün İngilizceyse menü adı **Tools → WebSocket Server Settings** olur.) [OBS uzaktan kontrol rehberi](https://obsproject.com/kb/remote-control-guide)

![OBS'de Araçlar menüsünden WebSocket Sunucusu Ayarları seçiliyor](docs/images/obs-tools-websocket.png)

Ayar penceresinde **WebSocket sunucusunu etkinleştir** seçeneğini işaretle. Portu `4455` bırakabilir veya uygulamada gireceğin portu kullan. **Kimlik Doğrulamasını Etkinleştir** seçili kalsın ve bir parola belirle. Sonra **Uygula** ve **Tamam**’a bas.

![OBS WebSocket sunucusunu etkinleştirme, port ve parola ayarları](docs/images/obs-websocket-settings.png)

OBS ayar penceresindeki **Bağlanma Bilgilerini Göster** düğmesine bas. Açılan ekranda bilgisayarın yerel IP adresi, port ve WebSocket parolası gösterilir. IP ve parolayı yalnızca kendi telefonundaki uygulamaya gir; bu bilgileri internette veya başkalarıyla paylaşma. QR kodu bu uygulamada kullanılmıyor.

![OBS bağlantı bilgileri penceresi; IP, port ve parola alanları](docs/images/obs-connection-info.png)

![Telefon, Wi‑Fi ve OBS bağlantı şeması](docs/images/wifi-connection.svg)

1. Telefon ve bilgisayar aynı Wi‑Fi ağına bağlı olsun.
2. Windows’ta Komut İstemi’ni açıp `ipconfig` çalıştır. Wi‑Fi adaptörünün **IPv4 Address** değerini bul (örneğin `192.168.1.25`). `127.0.0.1` yazma; bu telefonun kendisini gösterir.
3. Uygulamada **OBS ayarları** düğmesine dokun. Bilgisayarın IPv4 adresini, OBS portunu (`4455`) ve OBS’de belirlediğin WebSocket parolasını gir.

![Uygulama ana ekranında OBS ayarları düğmesi](docs/images/app-home-screen.jpg)

![Telefonda OBS WebSocket IP, port ve parola alanları](docs/images/app-obs-settings.jpg)

4. **Bağlan**’a bas. Durum alanında OBS bağlantısının açıldığını görmelisin.
5. Bağlanmazsa OBS’de WebSocket sunucusunun açık olduğunu, IP/port/parolanın doğru olduğunu ve Windows Güvenlik Duvarı’nın OBS’ye özel ağda izin verdiğini kontrol et.

**Bu sürüm Wi‑Fi kullanır; USB/ADB bağlantı seçeneği kaldırılmıştır.** WebSocket parolanı paylaşma. Her kullanıcı kendi OBS bilgisayarındaki parolayı uygulamaya girmelidir.

## Tuşları kullanma

- Ana ekrandan **+ TUŞ EKLE**’ye dokun, bir ad gir ve işlemi seç.
- Desteklenen işlemler: kaydı başlat, kaydı durdur, kaydı aç/kapat, sahne değiştir, mikrofonu sustur ve mikrofonun sesini aç.
- Sahne veya mikrofon işlemi seçtiğinde **OBS listesini göster** düğmesine dokunup OBS’deki adı seç. Gerekirse adı elle de yazabilirsin.
- Kaydet ve ana ekrandaki tuşa dokun. Bir tuşu basılı tutarak düzenleyebilir, sıralayabilir veya silebilirsin.
- Tuş boyutu kaydırıcısıyla ana ekrandaki tuşların yüksekliğini ayarla.
- **Profil değiştir** ile üç profil arasında geçiş yap. **Tuş sil** penceresi tüm profillerdeki tuşları listeler.

## Sınırlamalar

Bu sürüm yayın başlatma/durdurma, kamera kaynağını kapatma veya telefonu kilitleme işlemlerini içermez. Bağlantı ve OBS sahne/kaynak adları bilgisayardaki OBS kurulumuna bağlıdır; ilk kullanımdan önce her tuşu deneme sahnesinde kontrol et.

## Geliştirme

Android Studio’da projeyi açıp `app` modülünü çalıştır. APK üretmek için **Build → Generate APKs** seçeneğini kullan. Kaynak kod Kotlin, Jetpack Compose ve OBS WebSocket 5 kullanır.




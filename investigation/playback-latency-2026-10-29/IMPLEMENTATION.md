# Uygulanan oynatıcı düzeltmeleri

29 Ekim 2026. Önceki REPORT.md, değişiklik öncesi kaynak incelemesidir.

- Canlı düğmesinin tıklaması Canlı etiketinden bağımsız çalışır; işlemden önce başarı durumu atanmaz.
- LiveResumeListener, serviste gerçek pause→play geçişini yakalar ve canlı yayını varsayılan konuma taşır. Overlay/mini/bildirim/PiP/kulaklık için aynı politika uygulanır. Zaten oynarken değişen play nedeni yeniden seek yaptırmaz; VOD konumu korunur.
- Düşük gecikme START etiketinde PRECISE=YES kullanılır. 2 saniyelik segmentlerde hedef 3 saniyedir; önceki segment başına yuvarlanarak 4 saniye geriye gitmesi önlenir. Decode için önceki keyframe hâlâ kullanılabilir; hedef öncesi örnekler gösterilmez.
- Yakalama hızı düşük gecikmede en fazla 1.10x, normal modda 1.03x. Mevcut 2500/10000/600/1200 ms buffer ayarları korundu. Rebuffer başına sabit 200 ms hedef artırımı kapatıldı; Media3'ün buffer üzerinden güvenli hedef kestirimi korunur.
- Canlı göstergesi, sentetik PREFETCH sonu veya cihaz saati yerine timeline.defaultPosition ile currentPosition arasındaki ek mesafeyi kullanır. Bilinmeyen timeline Canlı sayılmaz; pause/buffering durumunda Canlı gösterilmez. Histerezis giriş/çıkış eşikleri 1500/3000 ms'dir. Gösterilen saniye kamera-ekran gecikmesi değildir.
- Manifest hedef süresi tüm segmentlerin en uzununa göre yukarı yuvarlanır. Yalnız ilk PREFETCH aktif segmente çevrilir. Mevcut START, VOD/ended/master ve native LL-HLS PART/PART-INF politikaları korunur.
- Debug derlemelerde KickLive etiketi timeline/hedef/buffer/seek durumlarını kaydeder. requestedSpeed kullanıcı hızıdır; Media3'ün dahili anlık yakalama hızının ölçümü olarak yorumlanmamalıdır. URL/token/hesap bilgisi loglanmaz.

## Doğrulama

Kotlin 2.4.21 ve projenin çözümlenmiş Media3 1.11.1/JUnit bağımlılıklarıyla iki üretim yardımcısı ve ilgili testler bağımsız derlenip çalıştırıldı: **13 test başarılı**. Normal Gradle testleri aynı test kaynaklarını kullanır.

Tam `:app:testDebugUnitTest :app:assembleDebug --offline` denemesi, oynatıcı dışındaki mevcut `ChatCommunityUi.kt` profil kartı çağrılarının imza uyumsuzluğuna takıldı (183 ve 272. satırlar). Bu nedenle tam test paketi/APK başarısı veya cihazda kurulmuş yeni sürüm iddia edilmez. İlgisiz profil bileşeni değiştirilmedi.

Gerçek web/Android saniye farkı ve uzun süreli rebuffer davranışı cihaz A/B ölçümü gerektirir; 13 test bu performans ölçümünün yerine geçmez.

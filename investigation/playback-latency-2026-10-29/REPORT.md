# Kick playback gecikmesi incelemesi — 29 Ekim 2026

Kaynak sürümü: `a06920e2154d6f922adcc7ba769bb31f2f838ee5`. Uygulama kodu değiştirilmedi. Hedef yayın: https://kick.com/rraenee. Kaynak kodu, tam Media3 1.11.1 bağımlılık kaynakları, güncel yayın manifesti ve sayfanın yüklediği dağıtılmış JavaScript incelendi. Kurulu APK'nin bu kaynak commit'iyle birebir eşleştiği doğrulanmadı.

## Sonuç

Kullanıcının gözlemini açıklayan iki kesin kaynak davranışı var: Canlı düğmesi 10.5 saniyeye kadar pencere mesafesinde etkisiz kalabiliyor; resume ve Canlı eylemleri manifestin segment başına yuvarlanan varsayılan konumuna dönüyor. Web ile ölçülmüş saniye farkı henüz yok. Aşağıdaki pencere mesafeleri kamera-ekran gecikmesi veya web'e göre fark değildir.

## Kanıt kaydı

| Kimlik | Tür ve kapsam | Kaynak |
|---|---|---|
| S1 | Doğrudan kaynak gözlemi | `KickPlayerView.kt:355–375, 426–435, 672–693, 1597, 1806–1819` |
| S2 | Doğrudan kaynak gözlemi | `PlaybackService.kt:90–105, 257–328, 396–412` |
| M1 | Tam kullanılan resmi bağımlılık kaynağı | Cached `media3-exoplayer-hls-1.11.1-sources.jar`, `HlsMediaSource.java:698–724`; `media3-exoplayer-1.11.1-sources.jar`, `ExoPlayerImplInternal.java:2718–2732`, `DefaultLivePlaybackSpeedControl.java:336–345, 394–400`, `DefaultLoadControl.java:812–828` |
| W1 | Dağıtılmış web paketi, statik | `web-player.js`, SHA256 `43aabc97041f7f741123390802e256563c25d8891f1af867bc9bcf70e8ccaed9`; sayfada gözlenen URL: `https://assets.kick.com/main/_next/static/chunks/1nfzjrv4_d1y0.js?dpl=474199c1` |
| H1 | Güncel manifest örneği | `rraenee-manifest-tags.txt`; 14 adet 2.000 saniye EXTINF, TARGETDURATION:6, iki PREFETCH, mevcut START/LL-HLS PART etiketleri gözlenmedi |
| R1 | Sınırlı çalışma zamanı gözlemi | Sayfada video elementi ve playback-monitor mesajları görüldü; tablette uygulama önde, MediaSession RRaenee/PLAYING. Kullanışlı liveOffset/speed/defaultPosition telemetrisi yok. Web örneğinde video paused/readyState=2 olduğundan gecikme ölçümüne elverişli değildi. |

REA analiz kanıtı: `ev_da93d7add1266f0e496bb937e98de7c359bac3b4208699d0ff36b32182468c02`. Özet görünüm: `ev_bea2ffb38fd9536f8e62402f3f73e23eef00f0c17e8d5f69ea72a9eea854e990`. Tek JavaScript dosyası başarıyla parse edildi (136 modül, parse failure yok); semantik grafik 20.000 node/unknown sınırına ulaştı ve kısmi. Bu, bütün web uygulamasının veya WASM worker davranışının çözüldüğü anlamına gelmez. Aşağıdaki web sonuçları ayrıca doğrudan handler gövdelerinden kontrol edildi.

Kanıt dosyaları repo dışında tutuldu:
`C:\Users\melih\.codex\visualizations\2026\10\29\01a18932-903b-7532-9e3e-1940fc1230c3\playback-latency-evidence`

## Bulgular

1. **Canlı yazması web'e yetişildiğini göstermiyor; düğme tıklaması yutulabiliyor.** [S1, kesin kaynak davranışı] Düşük gecikmede Canlı durumu 10.500 ms'ye kadar korunuyor, geriden Canlı durumuna dönüş eşiği 9.000 ms. Normal modda 14.500/12.500 ms. `onClick = { if (!isAtLiveEdge) onGoToLive() }` nedeniyle Canlı durumundayken kullanıcı yeniden senkronizasyon isteyemiyor. Örneğin 8 saniye pencere mesafesi gösterge tarafından Canlı sayılır ve tıklama seek yapmaz.

2. **Resume eski özetteki gibi tek bir 15 saniye kuralına bağlı değil.** [S1/S2, kesin] Overlay ve mini kontrollerin ortak `handleTogglePlay` yolu canlıda her resume öncesi `seekToDefaultPosition()` yapıyor. Listener'daki >15.000 ms kuralı ayrıca duruyor; harici play yollarında farklı davranış mümkün. Serviste `LiveAwarePlayer` yalnız boş bir ForwardingPlayer. Merkezi resume politikası yok.

3. **Varsayılan canlı hedefi segment sınırına yuvarlanıyor.** [S2/M1, kesin algoritma; H1 için koşullu hesap] Düşük gecikmede START=-1.5×son segment süresi enjekte ediliyor, PRECISE eklenmiyor. Media3 bağımsız part yoksa önceki segment başını seçiyor. Eşit 2 saniyelik segmentlerde sentetik pencere sonundan hedef 3 saniye olsa da başlangıç 4 saniyeye yuvarlanır. H1 örneği bu koşullara uyuyor; bu cihazdan ölçülmüş sonuç değildir. 4.167 saniyelik segment varsayımında benzer hesap 8.334 saniye verir. Kaynak START etiketi varsa uygulama onu korur ve hesap değişir.

4. **UI saniyesi gerçek yayın gecikmesiyle aynı ölçüm değil.** [S1/S2, kesin] Helper öncelikle `duration-currentPosition` kullanıyor; currentPosition>1000 değilse currentLiveOffset fallback'i var. Parser bitmemiş ilk PREFETCH'i tam süreli EXTINF gibi ekliyor. Böylece pencere sonu henüz tamamlanmamış segmentin varsayılan sonunu da içeriyor. Manifest yenilendikçe sayı basamaklı değişebilir. UI metriği, Media3'ün saat/PDT tabanlı hedefi ve IVS gecikmesi birbirinin yerine kullanılamaz. Bilgi yoksa helper'ın 0 döndürmesi de bilinmeyeni Canlı gibi sınıflandırabilir.

5. **Eski tuning artık geçerli değil.** [S2, kesin]

| Ayar | Eski paylaşılan özet | Güncel kaynak |
|---|---|---|
| min/max buffer | 5000/12000 ms | 2500/10000 ms |
| ilk oynatma/rebuffer | 1500/2500 ms | 600/1200 ms |
| düşük gecikme hızları | 0.95–1.10 | 0.97–1.03 |
| target/min/max offset | 4000/2500/8000 ms | Açık değer yok; manifestten türetiliyor |
| pencere mesafesi | currentLiveOffset öncelikli | duration-position öncelikli |

Sabit maksimum 1.03x ile fazladan 3 saniyeyi kapatmanın alt sınırı 3/0.03=100 saniye. Gerçek hız daha düşük olabilir; bazı gecikme adaptive hedef tarafından korunabilir. Bu matematiksel sınır, ölçülmüş toparlanma süresi değildir.

6. **Rebuffer sonrası hedef geriye kayabilir.** [S2/M1, kaynak mekanizması kesin; cihaz etkisi bilinmiyor] Her rebuffer hedefe 200 ms ekleyebiliyor. Default seek kullanıcı seek override'ını temizler, aynı ideal hedefte adaptive hedefin tamamen sıfırlanmasını garanti etmez. Ayrıca eski özetteki “her rebuffer tam 2500 ms bekletir” genellemesi doğru değil: LoadControl eşiği hedef canlı offset'in yarısıyla sınırlar.

7. **Web farklı bir oynatma motoru kullanıyor.** [W1, statik kesin] Paket IVS 1.56.2 oluşturuyor; worker/binary yolları `/ivs/1.56.2/amazon-ivs-wasmworker.min.*`. `toggleIsPlaying` IVS play/pause çağırıyor. `goBackToLive` DVR'den canlı kaynağa dönüyor ve playback rate'i 1'e alıyor; aynı canlı URL zaten seçiliyse bu handler'da açık bir seek yok. Bu handler'ı Android default seek ile eşdeğer sayamayız. IVS çekirdeğinin mevcut latency hedefi ve resume sırasında yaptığı iç işlemler bu incelemede ölçülmedi.

8. **TARGETDURATION dönüşümü ayrıca düzeltilmeye aday.** [S2, kesin] Son segment >2.5 saniyeyse koşulsuz 3 yazılıyor. Örneğin 4.167 saniyelik segmentte hedef süre gerçek segment için fazla kısa kalır. H1'in 2 saniyelik segmentlerinde bu özel sorun görülmüyor. Yenileme sıklığını değiştirmek gerçek segmenti kısaltmaz.

## Önerilen uygulama sırası

1. Açık Canlı tıklamasını durum etiketinden bağımsız çalıştır; seek tamamlanmadan `isAtLiveEdge=true` atama.
2. UI/mini/bildirim resume davranışını ortaklaştır. “Canlıya dön” hedefini segment erişilebilirliği ve ölçülmüş güvenli mesafeyle tanımla; pencerenin mutlak sonuna kör seek ekleme.
3. Ölçüm ekle: ham manifest segment/PREFETCH süreleri, timeline defaultPosition ve liveConfiguration.targetOffset, currentPosition/currentLiveOffset, pencere sonu mesafesi, bufferedDuration, playback speed, rebuffer sayısı ve seek nedeni. İmzalı URL/token loglama.
4. Aynı kalite ve ağda başlangıç, kısa/uzun pause, Canlı tıklaması ve rebuffer sonrası A/B ölç. Sonra 1.03 hız sınırını ve START/PRECISE politikasını test ederek değiştir. Yalnız buffer değerlerini düşürmek yeterli değil.

## Kontrol listesi ve sınırlar

- Güncel kodun eski özetten farkı: **yanıtlandı**.
- Canlı düğmesinin bazen işe yaramaması: **kaynak düzeyinde yanıtlandı**.
- Resume sonrası geride kalma: **mekanizma büyük ölçüde yanıtlandı**, cihaz telemetrisi eksik.
- Rraenee'nin gerçek segment yapısı: **tek güncel manifest örneğiyle yanıtlandı**; zamana/kaliteye göre değişim örneklenmedi.
- Web'in kaç saniye önde olduğu: **çözülmedi**; eşzamanlı frame/PDT veya ses karşılaştırması yapılmadı.
- Rebuffer hedef birikiminin cihazdaki etkisi: **çözülmedi**.

REA MCP araçları oturumda kayıtlı değildi. İstenen becerinin doctor adımı eski skill/kayıt ve desteklenmeyen Node sürümü bildirdi. Setup çalıştırılmadı, yapılandırma değiştirilmedi. CLI ile statik analiz tamamlandı; ilk sandbox geçici dosya erişim hatası normal erişimle giderildi. Uygulama kodu değiştirilmediği için build/test çalıştırılmadı.

Resmi referanslar: [Media3 canlı yayın davranışı](https://developer.android.com/media/media3/exoplayer/live-streaming), [tam 1.11.1 HlsMediaSource kaynağı](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java), [IVS oynatıcı açıklaması](https://docs.aws.amazon.com/ivs/latest/LowLatencyUserGuide/player.html).

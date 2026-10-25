# Yapılacaklar: `refactor-project` dalını yerelde devreye alma (sunucu)

Bu not, bu daldaki işi yerel makinede devreye alacak kişi ya da Claude için yazıldı. Adımları sırayla uygula; bir adım beklenmedik sonuç verirse dur ve kullanıcıya sor.

Uygulamanın notu `hemen-lazim-app` reposunda, aynı adlı dalda `YAPILACAKLAR.md`. **İki taraf birlikte güncellenmeli:**
- Eski uygulama bu sunucuyla talep açamaz: süre artık dakika cinsinden, kategori ve birim adları ASCII.
- Yeni uygulama da eski sunucuyla çalışmaz.

## Bu dalda ne var
`main`'e göre farklar: `git log --oneline origin/main..refactor-project`. Özetle:
- **Enum adları:** kategori ve birim enum'ları ASCII (`YEMEK_MALZEMESI`, `HIJYEN`, `PARCA`, `LITRE`, `DIGER`…). Veritabanı göçü gerekiyor (adım 4).
- **Yakındaki talepler:** daire kesişimi her yarıçapta doğru; kişinin kendi talebi listede yok.
- **Süre:** talep süresi dakika cinsinden (`expiresInMinutes`). `RequestExpiryJob` süresi dolan açık talepleri dakikada bir `EXPIRED` yapıyor.
- **Süre uzatma:** talep sahibi uzatabilir: `POST /api/requests/{id}/extend`, gövde `{"minutes": 15–1440}`, en fazla şimdiden 24 saat.
- **Sohbet güvenliği:** sohbet yalnızca odanın iki üyesine açık; STOMP bağlantısı token istiyor ve gönderen kimlikten alınıyor.
- **Teklif bildirimi:**
  - Talep sahibine, talep başına bir kez gidiyor.
  - Push metin değil yerelleştirme anahtarı taşıyor: `notif_supply_offer_title` ve `notif_supply_offer_body`.
- **Çıkış:** cihaz kaydını silen uç: `POST /api/notifications/fcm-token/unregister`.
- **Hata biçimi:** her hata aynı biçimde: `{"code": "...", "message": "...", "fieldErrors": {...}}`.
  - Kodlar `errors/ErrorCode.java`'da.
  - `message` İngilizce ve yalnızca geliştirici ile log için. Kullanıcıya gösterilecek metni uygulama koda göre seçiyor.
- **Kayıt ve sağlık ucu:** kayıtta sunucu tarafı doğrulama var; `/actuator/health` ayrıntı göstermiyor.

## Ortam (bu dalda değişmedi)
- **Veritabanı:** MySQL yerelde. Bağlantı ortam değişkenlerinden: `DB_URL`, `DB_USER`, `DB_PASS`. JWT için `HEMEN_LAZIM_SECRET`.
- **Firebase Admin anahtarı:** çalışma dizinine göre `config/hemen-lazim-firebase-adminsdk.json`. Git'e girmez.
- **Port:** 8080 (varsayılan).
- **Redis:** sunucu kullanmıyor; pom'da Redis bağımlılığı yok, Docker'daki Redis bu dal için gerekmiyor.
- **Değişmeyenler:**
  - Yeni ayar ya da ortam değişkeni yok.
  - Yeni tablo yok; `ddl-auto=update` kalıyor.
  - Sohbet geçmişi korunuyor; oda kimliği biçimi aynı.

## Adımlar
1. **Dalı al:** `git fetch origin` ve `git checkout refactor-project`.
   - Bu dalın eski adı `claude/great-thompson-j0w9ae`. GitHub'da hâlâ duruyorsa sil; içeriği bu dalda.
   - Önce doğrula: `git merge-base --is-ancestor origin/claude/great-thompson-j0w9ae origin/refactor-project` hatasız dönmeli.
   - Sonra sil: `git push origin --delete claude/great-thompson-j0w9ae`.
2. **Testler:** `./mvnw test -Dtest='*Test'` (Windows: `mvnw.cmd`). 84 test geçmeli; veritabanı gerekmez, repository testleri H2 kullanıyor.
   - `HemenLazimApplicationTests` (`*Tests`) gerçek MySQL'e bağlanır, `DB_*` değişkenleri tanımlı olmalı. Göçten sonra tam `./mvnw test` ile çalıştırılabilir.
3. **Eski sunucuyu durdur.**
4. **Veritabanını yedekle:** `mysqldump -u <DB_USER> -p <veritabanı> > yedek-<tarih>.sql`.
   - Veritabanı adını `DB_URL`'den al.
   - Şifreyi kullanıcıdan iste, hiçbir dosyaya yazma.
5. **Göç (bir kez, yeni sunucu başlamadan önce):** `src/main/java/com/tpl/hemen_lazim/enum_ascii_migration.sql` dosyasını aynı veritabanında çalıştır.
   - `material_requests` tablosundaki `category` ve `units` sütunlarını native ENUM'dan VARCHAR'a çeviriyor, Türkçe karakterli değerleri ASCII'ye dönüştürüyor.
   - Sondaki kontrol sorgusu **boş** dönmeli. Dönmezse sunucuyu başlatma; satırları kullanıcıya göster.
   - İkinci kez çalıştırmak zarar vermez.
   - Atlanırsa eski talepler okunamaz ve listeleme 500 verir.
6. **Derle ve başlat:** ortam değişkenleri tanımlıyken `./mvnw clean package -DskipTests` ve `java -jar target/hemen_lazim-0.0.1-SNAPSHOT.jar`. Ya da `./mvnw spring-boot:run`. Eski jar'ı yeniden başlatmak yetmez.
7. **Duman testi** (curl, `http://localhost:8080`):
   - `GET /actuator/health` → `{"status":"UP"}`.
   - Token'sız `GET /api/requests/mine` → 401, gövde `{"code":"UNAUTHORIZED",...}`.
   - `POST /auth/login`, gövde `{"userName":"<var olan kullanıcı>","userPassword":"yanlis"}` → 401, `"code":"INVALID_CREDENTIALS"`.
   - Doğru bilgilerle giriş → 200, yanıtta `data.accessToken`. O token'la (`Authorization: Bearer …`) `GET /api/requests/mine` → 200; eski talepler hata vermeden listelenmeli. Bu, göçün doğrulaması.
   - Loglarda başlangıç hatası olmamalı. Süresi geçmiş açık talep varsa bir dakika içinde "… talebin süresi doldu, EXPIRED yapıldı" logu görünür.
   - Başlangıçta "Firebase Admin SDK initialized successfully" logu görünmeli. Yerine "Failed to initialize Firebase Admin SDK" ya da "not configured" çıkarsa push çalışmaz; kullanıcıya söyle.
8. **Uygulamayla birlikte dene:** uygulamanın aynı adlı dalındaki `YAPILACAKLAR.md`'ye geç.
9. **Bitince:**
   - Sonucu kullanıcıya özetle.
   - Her şey yolundaysa `refactor-project`'i `main`'e birleştirmeyi öner. Kullanıcı onaylamadan birleştirme ve `main`'e push'lama.

## Kurallar (bu dalda yapılan değişikliklerle gelen)
- **Hata kodları:** sunucuya yeni bir kod eklenirse uygulamada `network/ApiErrors.kt` (`ApiCodes`) ile `ui/ErrorMessages.kt` eşlemesi de güncellenmeli. Eşlenmeyen kod HTTP durumuna göre genel bir metne düşer. HTTP durumlarını değiştirme: uygulamanın token yenileme mantığı bunlara bakıyor.
- **Push sözleşmesi:**
  - `NotificationService.SUPPLY_OFFER_TITLE_KEY` ve `SUPPLY_OFFER_BODY_KEY` adları uygulamanın `strings.xml` dosyalarında (iki dilde) aynen bulunmalı.
  - Gövdenin tek argümanı talebin başlığı.
  - Bildirime düz `title`/`body` ekleme: Android onları anahtarlara tercih eder ve dil seçimi bozulur.
- **Dil:** API mesajları İngilizce kalır; kullanıcıya gösterilen metin uygulamada.
- **Gizli bilgiler:** `config/*.json`, ortam değişkenleri ve şifreler commit'lenmez.
- **Commit mesajları:** İngilizce, kısa ve ne yaptığını söyleyen cümle. Kod yorumları Türkçe.

## Sonra (ayrı işler, kullanıcı onayıyla)
- Giriş uçlarına hız sınırı.
- `ddl-auto=update` yerine Flyway göçleri.
- Konum gizliliği: başkalarına gösterilen koordinat 200–300 m bulanıklaştırılsın; eşleşme gerçek konumla yapılsın.
- Hesap silme (`DELETE /api/users/me`): mesajlar, refresh token'lar ve cihaz kayıtları da silinsin.
- HTTPS/WSS.
- Ürün:
  - Teklif tablosu: kabul ve ret, ilan `MATCHED` olur.
  - İlana bağlı sohbet.
  - Puanlama.

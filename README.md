# อ่านดี OCR — Android starter

ต้นแบบแอป Android สำหรับอ่านภาษาไทยและอังกฤษจาก JPG, PNG และ PDF ด้วย Tesseract (Tesseract4Android)
ทำงานบนเครื่อง ไม่มี API key ไม่มีสิทธิ์ INTERNET และไม่มีการอัปโหลดเอกสาร

## สถานะ

**สร้าง APK และตรวจ Android Lint ผ่านบน GitHub Actions แล้ว**
ผล build: https://github.com/pharmdever/thai-ocr-android/actions/runs/42023543299
ยังไม่ได้ทดสอบบนมือถือจริง ต้องตรวจ OCR กับเอกสารจริงก่อนนำไปใช้งาน

## ขั้นที่ 1: นำโปรเจกต์ขึ้น GitHub

1. สร้าง repository ชื่อ `thai-ocr-android` ในบัญชี GitHub ของคุณ จะเลือก Private ก็ได้
2. แตก ZIP แล้วนำไฟล์ภายในโฟลเดอร์ `thai-ocr` ขึ้น repository ให้ `settings.gradle` อยู่ที่ราก
3. ต้องนำโฟลเดอร์ซ่อน `.github/workflows/android.yml` ขึ้นด้วย แนะนำใช้ Git หรือ GitHub Desktop เพื่อไม่ให้ไฟล์ซ่อนตกหล่น
4. ใช้ชื่อ branch `main` หรือเปิด Actions → Build Android APK → Run workflow

## ขั้นที่ 2: ดาวน์โหลด APK ทดสอบ

1. เปิดแท็บ Actions → Build Android APK แล้วรอผลสำเร็จ
2. ในหน้า workflow เลื่อนลง Artifacts → ดาวน์โหลด `thai-ocr-debug-apk` แล้วแตก ZIP
3. ส่ง `app-debug.apk` ไปยังมือถือ Android 8 ขึ้นไป
4. อนุญาตติดตั้งจากแหล่งที่ใช้เปิดไฟล์ แล้วติดตั้ง

APK นี้เป็น **debug สำหรับทดสอบ** เซ็นด้วย debug key ของ runner ซึ่งอาจเปลี่ยนในแต่ละ build
การอัปเดตทับจึงอาจทำไม่ได้และต้องถอนแอปเดิมก่อน ก่อนแจกใช้ต่อเนื่องต้องตั้ง signing key ถาวร
เตรียมคีย์ถาวรแล้ว แต่ต้องเพิ่ม GitHub Secrets ตามขั้นตอนด้านล่างก่อนสร้าง release

## ขั้นที่ 3: ทดสอบบนเครื่องจริง

- เปิดโหมดเครื่องบินหลังติดตั้ง แล้วลองภาพพิมพ์ไทยและอังกฤษ ตรวจว่าอ่านได้โดยไม่ใช้อินเทอร์เน็ต
- ลอง PDF สแกน 2–3 หน้า ตรวจจำนวนหน้าและลำดับข้อความ
- แก้ข้อความ คัดลอก และบันทึก .txt แล้วเปิดตรวจภาษาไทย
- ลองยกเลิกตัวเลือกไฟล์, PDF ล็อกรหัสผ่าน, PDF เกิน 30 หน้า และไฟล์เกิน 50 MB
- ลองบนมือถือ RAM ต่ำและภาพหมุน ตรวจความเร็วและคุณภาพข้อความ

## สร้างจากเครื่องตัวเอง

ต้องมี JDK 17, Android SDK 35 และ Gradle 8.11.1

```bash
bash scripts/prepare-models.sh
gradle assembleDebug lintDebug
```

ผลลัพธ์: `app/build/outputs/apk/debug/app-debug.apk`
หาก Android SDK ไม่อยู่ในตำแหน่งมาตรฐาน ให้ตั้ง `ANDROID_HOME` หรือสร้าง `local.properties`
โดยระบุ `sdk.dir` ตามเครื่องของคุณ ไม่ต้อง commit ไฟล์นี้

## ขอบเขตเวอร์ชันแรก

- เลือกทีละไฟล์ ภาพ JPG/PNG หรือ PDF สูงสุด 30 หน้าและขนาดไม่เกิน 50 MB
- PDF ถูกเรนเดอร์ทีละหน้า และภาพย่อเพื่อจำกัดหน่วยความจำ
- ไทยและอังกฤษถูกรวมไว้ใน APK โดยดาวน์โหลดชุดภาษาตอน build ครั้งแรก
- ไม่เก็บประวัติ ไม่ทำงานเบื้องหลัง ไม่มีถ่ายกล้องในแอป และยังไม่แก้การหมุนภาพ EXIF อัตโนมัติ
- ออกจากแอปหรือระบบปิดแอปอาจทำให้ข้อความที่ยังไม่บันทึกหาย
- OCR ไม่รักษารูปแบบตาราง และไม่รับประกันความแม่นยำของลายมือหรือภาพเอียง
- ใช้ Tesseract4Android 4.9.0 ผ่าน JitPack ต้องทดสอบความเข้ากันได้และการติดตั้งบน Android รุ่นที่ต้องการใช้งาน

## ค่าใช้จ่ายและคีย์

ไม่มีค่า OCR ต่อหน้าและไม่ต้องใช้คีย์ผู้ให้บริการ OCR
GitHub Actions ใช้โควตาตามแผนบัญชี โดยเฉพาะ private repository ควรดู Billing → Actions ก่อน build บ่อย
ไม่ต้องซื้อโดเมนหรือเช่า backend เพื่อใช้งาน APK นี้
ไม่ต้องสร้าง GitHub token แล้วใส่ไว้ในโค้ด

## ส่วนประกอบและลิขสิทธิ์

- Tesseract4Android 4.9.0: https://github.com/adaptech-cz/Tesseract4Android (Apache-2.0)
- ชุดภาษา tessdata_fast 4.1.0: https://github.com/tesseract-ocr/tessdata_fast (Apache-2.0)
- สคริปต์ build แนบ LICENSE ของชุดภาษาไว้ใน assets อัตโนมัติ

## คีย์ถาวรและอัปเดตทับ

สร้างคีย์ถาวรแยกจาก repository แล้ว ไม่ได้ commit คีย์หรือรหัสผ่าน
ไฟล์สำรองส่วนตัวส่งให้เจ้าของแอปแยกต่างหาก ต้องเก็บไว้ในที่ปลอดภัย
ขณะนี้ยังไม่ได้เพิ่ม Secrets และยังไม่ได้ทดสอบ signed release

ตั้งค่าครั้งแรก:

1. แตกไฟล์สำรองส่วนตัว ห้ามอัปโหลด ZIP หรือไฟล์ข้างในเข้า repository
2. เปิด https://github.com/pharmdever/thai-ocr-android/settings/secrets/actions
3. กด **New repository secret** ชื่อ `ANDROID_KEYSTORE_BASE64` แล้วใส่ข้อความทั้งหมดจากไฟล์ชื่อเดียวกันที่ลงท้าย `.txt`
4. เพิ่ม secret ชื่อ `ANDROID_KEYSTORE_PASSWORD` แล้วใส่ข้อความจากไฟล์ชื่อเดียวกันที่ลงท้าย `.txt`
5. เปิด Actions → **Build signed release APK** → **Run workflow** เลือก branch `main`
6. เมื่อสำเร็จ ดาวน์โหลด artifact `thai-ocr-release-…` แตก ZIP แล้วติดตั้งไฟล์ `.apk`

รุ่น debug ก่อนหน้าใช้คนละคีย์ ต้องถอนแอป debug แล้วติดตั้ง release ครั้งเดียว
บันทึกข้อความที่ต้องการเก็บก่อนถอนแอป หลังจากนั้นใช้ signed release ทุกครั้ง

การออกเวอร์ชันใหม่:

- แก้โค้ดใน branch `main` แล้วเรียก **Build signed release APK** อีกรอบ
- ระบบกำหนด versionCode = 100 + หมายเลขรันของ workflow และ versionName = `0.2.<หมายเลขรัน>`
- ใช้ **Run workflow** ใหม่เมื่อจะออกรุ่นใหม่ การกด **Re-run jobs** ใช้เลขเวอร์ชันเดิม
- อย่าลบและสร้าง release workflow ใหม่หรือรีเซ็ตหมายเลขรัน ถ้าจำเป็นต้องย้ายระบบ build ให้ตั้ง versionCode มากกว่ารุ่นล่าสุดที่แจก
- ใช้ applicationId `com.thaio.ocr` และคีย์เดิมตลอด เพื่อให้ Android อัปเดตทับพร้อมคงข้อมูลแอปได้
- การติดตั้งอัปเดตยังต้องเปิด APK และยืนยันติดตั้งเอง เวอร์ชันนี้ยังไม่มีระบบดาวน์โหลดอัปเดตในแอป
- เก็บ APK รุ่นที่แจกและไฟล์สำรองคีย์ไว้เอง artifact ใน GitHub มีอายุ 30 วัน

คีย์ alias: `thai-ocr` / ประเภท: PKCS12 / RSA 4096

ใบรับรอง SHA-256 (เป็นข้อมูลสาธารณะ ใช้ตรวจว่าคีย์ตรงกัน):

```text
65:F6:64:BC:A0:EB:27:DE:45:C7:E1:72:26:76:AE:D5:80:29:E3:BE:C6:DA:D2:65:D6:D0:79:B2:9B:A3:66:8A
```

workflow จะตรวจลายเซ็นกับ fingerprint นี้ก่อนปล่อย artifact
หากคีย์สูญหาย อย่าสร้างคีย์ใหม่แทนสำหรับแอปที่แจกแล้ว ให้กู้คืนจากไฟล์สำรองเดิม

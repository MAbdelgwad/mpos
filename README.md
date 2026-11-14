# MPOS — Modular Spring application

تطبيق نقاط بيع قيد التحويل إلى Java 21 / Spring Boot وPostgreSQL، مع واجهة React مستقلة. المنجز حاليًا: الهوية والصلاحيات والبيانات الأساسية والمشتريات والمخزون. المبيعات وPOS والمدفوعات والمرتجعات والتقارير لم تكتمل.

| المجلد | الوظيفة |
| --- | --- |
| `frontend/` | React ومصدر الواجهة وإعدادات npm وDockerfile |
| `backend/` | Spring Boot واختبارات Java وترحيلات Flyway |
| `infra/` | إعدادات Nginx والبنية التشغيلية |
| `docs/` | قواعد البزنس والتوثيق المعماري |
| `scripts/` | التشغيل والفحص والإيقاف |
| `legacy/laravel/` | مرجع الكود القديم، خارج البناء والتشغيل الجديدين |

## التشغيل للتجربة

من جذر المشروع، بعد تثبيت Docker وCompose v2 وPython 3:

```bash
bash scripts/start.sh
```

افتح http://localhost:18181 . يعرض السكربت البريد وكلمة المرور بعد نجاح الفحص. يولّد كلمات مرور محلية ويحفظها في `.env` بصلاحية 600؛ لا ترفع هذا الملف. يستخدم السكربت مشروع Docker مستقلًا `mpos-test-architecture` وقاعدة بيانات جديدة منفصلة عن حزمة المرحلة الثالثة. لا تحتاج Java أو Node محليًا للتشغيل باستخدام Docker. أول تشغيل يحتاج إنترنت لتنزيل الاعتماديات.

```bash
bash scripts/verify.sh
bash scripts/logs.sh
bash scripts/stop.sh
python3 scripts/setup.py --show-login
```

الإيقاف يحفظ البيانات. `down -v` يحذفها؛ لا تستخدمه لمجرد إعادة التشغيل. يمكن تغيير المنافذ في `.env`. تغيير كلمة مرور Bootstrap في الملف لا يعيد تعيين حساب موجود. إذا غيّرت كلمة المرور في التطبيق، حدّث قيمة `.env` حتى ينجح فحص الدخول.

## التطوير

Java 21 JDK وMaven 3.9+ للخلفية، وNode 22 للواجهة:

```bash
cd backend
mvn verify
```

ومن جذر المشروع:

```bash
cd frontend
npm ci --legacy-peer-deps --no-audit --no-fund
npm run build
```

اختبارات التكامل تستخدم PostgreSQL حقيقيًا مؤقتًا، وتحتاج مستخدمًا غير root. ملفات Flyway V1–V3 لم تتغير أثناء إعادة الهيكلة. راجع `docs/architecture-ar.md` للحدود المعمارية وما بقي من تحسينات، و`docs/inventory-purchases-ar.md` لسيناريو اختبار المشتريات والمخزون.

اختبارات Java وبناء React لا تعني أن Docker أو تجربة المتصفح تحققت؛ تشغيلهما ما زال يحتاج التحقق على جهازك. لا تستخدم هذه النسخة للمبيعات الفعلية.

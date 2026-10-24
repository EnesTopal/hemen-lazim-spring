-- KATEGORİ / BİRİM ENUM ADLARINI ASCII'YE ÇEVİRME (bir kerelik)
-- Yeni sunucu sürümünü başlatmadan ÖNCE çalıştır; aksi halde eski değerli satırlar okunamaz.
-- Hibernate 6 bu sütunları MySQL'de native ENUM olarak açmış olabilir; yeni değerler eski ENUM
-- listesinde olmadığından önce sütunlar VARCHAR'a çevrilir (entity'de artık VARCHAR tanımlı).

SET SQL_SAFE_UPDATES = 0;

ALTER TABLE `material_requests` MODIFY `category` VARCHAR(40) NOT NULL;
ALTER TABLE `material_requests` MODIFY `units` VARCHAR(20) NULL;

UPDATE `material_requests` SET `category` = CASE `category`
    WHEN 'YEMEK_MALZEMESİ' THEN 'YEMEK_MALZEMESI'
    WHEN 'HİJYEN'          THEN 'HIJYEN'
    WHEN 'TEMİZLİK'        THEN 'TEMIZLIK'
    WHEN 'GİYİM'           THEN 'GIYIM'
    WHEN 'KOZMETİK'        THEN 'KOZMETIK'
    WHEN 'ELEKTRONİK'      THEN 'ELEKTRONIK'
    WHEN 'KIRTASİYE'       THEN 'KIRTASIYE'
    WHEN 'OTHER'           THEN 'DIGER'
    ELSE `category` END;

UPDATE `material_requests` SET `units` = CASE `units`
    WHEN 'PARÇA' THEN 'PARCA'
    WHEN 'LİTRE' THEN 'LITRE'
    WHEN 'DİĞER' THEN 'DIGER'
    WHEN 'OTHER' THEN 'DIGER'
    ELSE `units` END;

SET SQL_SAFE_UPDATES = 1;

-- KONTROL: boş dönmeli.
-- İkili karşılaştırma şart: varsayılan utf8mb4_0900_ai_ci harmanlaması 'İ' ile 'I'yı eşit
-- sayar, düz karşılaştırma dönüşmemiş satırları gözden kaçırır.
SELECT `id`, `category`, `units`
FROM `material_requests`
WHERE CAST(`category` AS BINARY) NOT IN ('YEMEK', 'YEMEK_MALZEMESI', 'HIJYEN', 'TEMIZLIK', 'GIYIM',
                                         'KOZMETIK', 'ELEKTRONIK', 'KIRTASIYE', 'DIGER')
   OR (`units` IS NOT NULL
       AND CAST(`units` AS BINARY) NOT IN ('PARCA', 'ADET', 'PAKET', 'KG', 'LITRE', 'KUTU', 'DIGER'));

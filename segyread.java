import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class segyread {

    int ns;
    int sampleSize;
    int formatCode;
    int numberOfTraces;
    int traceSize;
    int headerSize = 240;
    float[] data;
    float max = 0;

    // ByteOrder'ı dışarıdan belirleme opsiyonu (Sorun yaşarsan LITTLE_ENDIAN yapabilirsin)
    private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN; 

    public void read(String filePath) {
        File file = new File(filePath);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            
            // --- 1. Başlıkları Güvenli Okuma (ByteBuffer ile Endianness kontrolü) ---
            byte[] headerBuffer = new byte[3600];
            raf.readFully(headerBuffer);
            ByteBuffer bbHeader = ByteBuffer.wrap(headerBuffer);
            bbHeader.order(byteOrder);

            ns = bbHeader.getShort(3220);
            formatCode = bbHeader.getShort(3224);
            
            System.out.println("Okunan ns: " + ns);
            System.out.println("Okunan formatCode: " + formatCode);

            // Eğer formatCode saçma sapan bir şey çıkarsa, dosya Little Endian olabilir.
            if (formatCode < 1 || formatCode > 8) {
                System.err.println("UYARI: Format kodu geçersiz (" + formatCode + "). Dosya Little-Endian olabilir. ByteOrder'ı değiştirip tekrar deneyin.");
                // byteOrder = ByteOrder.LITTLE_ENDIAN; // İhtiyaç halinde aktif et
            }

            sampleSize = getSampleSize(formatCode);
            numberOfTraces = calculateTrace(file);
            traceSize = ns * sampleSize;
            
            System.out.println("Hesaplanan Trace Sayısı: " + numberOfTraces);

            data = new float[numberOfTraces * ns];
            max = 0;
            int k = 0;

            // Okuma pointer'ını veri başlangıcına (3600) al
            raf.seek(3600);

            // --- 2. Veri Okuma Döngüsü ---
            for (int i = 0; i < numberOfTraces; i++) {
                raf.skipBytes(240); // Her trace'in 240 byte'lık header'ını atla
                
                byte[] traceBuffer = new byte[traceSize];
                raf.readFully(traceBuffer); 
                
                ByteBuffer bb = ByteBuffer.wrap(traceBuffer);
                bb.order(byteOrder); // Endianness kuralını uygula

                for (int j = 0; j < ns; j++) {
                    if (formatCode == 1) { // IBM Float
                        int ibmInt = bb.getInt();
                        data[k] = ibmToFloat(ibmInt); 
                    } else if (formatCode == 5) { // IEEE Float (Standart Java Float)
                        data[k] = bb.getFloat(); 
                    } else {
                        throw new RuntimeException("Bu format henüz desteklenmiyor: " + formatCode);
                    }

                    // Max değer hesaplaması (Sıfıra bölme hatasını engellemek için kontrol)
                    if (Math.abs(data[k]) > max) {
                        max = Math.abs(data[k]);
                    }
                    k++;
                }
            }

            System.out.println("Okuma bitti. Max Genlik: " + max);

            // --- 3. Normalizasyon ---
            if (max > 0) { // Max 0 ise bölme işlemi yapma (NaN hatasını önler)
                for (int i = 0; i < data.length; i++) {
                    data[i] = data[i] / max;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Daha güvenilir ve standart IBM'den IEEE Float'a dönüşüm metodu
    public float ibmToFloat(int ibmVal) {
        // Sadece 24 bitlik kesir kısmını al
        int fconv = ibmVal & 0x00FFFFFF;
        
        // Kesir sıfırsa sayı sıfırdır
        if (fconv == 0) {
            return 0.0f;
        }

        // İşaret (1 bit) ve Üs (7 bit) değerlerini ayır
        int fsign = (ibmVal >> 31) & 1;
        int fexp = (ibmVal >> 24) & 0x7F;

        // Kesrin başındaki sıfırları say (32 bitlik int olduğu için baştaki 8 biti çıkarıyoruz)
        int shift = Integer.numberOfLeadingZeros(fconv) - 8;
        
        // Kesri normalize et (Sola kaydırarak ilk "1" bitini 23. sıraya daya)
        fconv <<= shift;

        // Doğru IEEE 754 Üs Hesaplaması
        // fexp << 2 işlemi (fexp * 4), 16 tabanını 2 tabanına çevirir!
        int ieeeExp = (fexp << 2) - 130 - shift;

        // Underflow (Çok küçük sayı) kontrolü
        if (ieeeExp <= 0) {
            return 0.0f; 
        }
        // Overflow (Çok büyük sayı) kontrolü
        if (ieeeExp >= 255) {
            return (fsign == 1) ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY; 
        }

        // İşaret, yeni üs ve gizli bit (23. bit) atılmış kesri birleştir
        int ieeeVal = (fsign << 31) | (ieeeExp << 23) | (fconv & 0x007FFFFF);
        
        return Float.intBitsToFloat(ieeeVal);
    }

    public int calculateTrace(File file) {
        long fileSize = file.length();
        long headerSize = 3600; // Text header (3200) + Binary header (400)
        long traceBlockSize = 240 + (ns * sampleSize); // 1 Trace header + Data
        return (int) ((fileSize - headerSize) / traceBlockSize);
    }

    public int getSampleSize(int formatCode) {
        if (formatCode == 3) return 2;
        if (formatCode == 8) return 1;
        return 4; 
    }
}
import imgui.ImGui;
import imgui.ImVec2;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL30.*; // GL_R32F ve GL_RED gibi modern formatlar için
import static org.lwjgl.opengl.GL31.*; // Primitive Restart için
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;



public class Wiggle {
	
	
	   
	
	public void wiggle1(int not, float[] data, float[] gain, int ns, ImVec2[] tracePoints, int visible) {
	    var drawList = ImGui.getBackgroundDrawList();
	    
	    float screenWidth = ImGui.getIO().getDisplaySizeX();
	    float screenHeight = ImGui.getIO().getDisplaySizeY();

	    int visibleTraces = Math.min(not, visible); 
	    
	    // DÜZELTME: Ekranın solundan ve sağından yarım kanal boşluk bırakıyoruz
	    // Böylece ilk kanal 0'dan değil, bir miktar içeriden başlıyor.
	    float margin = screenWidth / (visibleTraces + 1); 
	    float dynamicTraceSpacing = (screenWidth - (2 * margin)) / Math.max(1, visibleTraces - 1);
	    
	    float dynamicSampleSpacing = screenHeight / ns;
	    float currentGain = gain[0];

	    for (int i = 0; i < visibleTraces; i++) {
	        // xAnchor artık margin kadar içeriden başlıyor
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        
	        for (int j = 0; j < ns; j++) {
	            int idx = i * ns + j;
	            
	            float px = xAnchor + (data[idx] * currentGain);
	            float py = j * dynamicSampleSpacing;
	            
	            tracePoints[j].set(px, py);
	        }
	        
	        drawList.addPolyline(tracePoints, ns, 0xFF000000, 0, 1.0f);
	    }
	}
	 
	

	public void wiggle2(int not, int visible, float[] gain, int ns, float[] data) {
	    // 1. Güncel Ekran Boyutlarını al
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();

	    glViewport(0, 0, (int)sw, (int)sh); 

	    glPushAttrib(GL_ALL_ATTRIB_BITS);
	    glDisable(GL_TEXTURE_2D);

	    glMatrixMode(GL_PROJECTION);
	    glPushMatrix();
	    glLoadIdentity();
	    glOrtho(0, sw, sh, 0, -1, 1); 
	    
	    glMatrixMode(GL_MODELVIEW);
	    glPushMatrix();
	    glLoadIdentity();

	    // 2. Dinamik Spacing ve Kenar Boşluğu Hesaplaması
	    int visibleTraces = Math.min(not, visible); 
	    
	    // DÜZELTME: wiggle1'deki gibi güvenli bir margin (kenar boşluğu) hesaplıyoruz
	    float margin = sw / (visibleTraces + 1); 
	    // İzler arası boşluğu, ekranın iki yanından margin çıkararak hesapla
	    float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, visibleTraces - 1);
	    float dynamicSampleSpacing = sh / (ns > 0 ? ns : 1);
	    float currentGain = gain[0];

	    // --- Çizim Döngüsü ---
	    glColor3f(0.0f, 0.0f, 0.0f); // Siyah Wiggle
	    for (int i = 0; i < visibleTraces; i++) {
	        // xAnchor başlangıcına margin ekleyerek ilk kanalı içeri kaydırıyoruz
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        
	        glBegin(GL_LINE_STRIP);
	        for (int j = 0; j < ns; j++) {
	            int idx = i * ns + j;
	            float px = xAnchor + (data[idx] * currentGain);
	            float py = j * dynamicSampleSpacing;
	            glVertex2f(px, py);
	        }
	        glEnd();
	    }

	    // Ayarları geri yükle
	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	    glPopAttrib();
	}
	 
	public void wiggle3(int shaderProgram, int textureId, float max, int ns, float[] gain, int ntr, int visible) {
	    if (shaderProgram <= 0 || textureId <= 0) return;

	    glUseProgram(shaderProgram);

	    // 1. Ekran Boyutlarını Al
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();
	    int visibleTraces = Math.min(ntr, visible);
	    glViewport(0, 0, (int)sw, (int)sh);

	    // 2. Uniform Lokasyonlarını Bul
	    int projLoc = glGetUniformLocation(shaderProgram, "projection");
	    int gainLoc = glGetUniformLocation(shaderProgram, "wiggleGain");
	    int traceLoc = glGetUniformLocation(shaderProgram, "currentTrace");
	    int sampleLoc = glGetUniformLocation(shaderProgram, "numSamples");
	    int hLoc = glGetUniformLocation(shaderProgram, "screenHeight");
	    
	    // YENİ EKLENEN UNIFORMLAR
	    int totalVisibleLoc = glGetUniformLocation(shaderProgram, "totalVisibleTraces");
	    int screenWidthLoc = glGetUniformLocation(shaderProgram, "screenWidth");

	    // 3. Projeksiyon Matrisini Shader'a Gönder
	    float[] orthoMatrix = {
	        2.0f/sw,  0,        0,    0,
	        0,       -2.0f/sh,  0,    0,
	        0,        0,       -1.0f, 0,
	       -1.0f,     1.0f,     0,    1.0f
	    };
	    glUniformMatrix4fv(projLoc, false, orthoMatrix);

	    // 4. Uniform Değerlerini Güncelle
	    // SİLDİĞİMİZ: xSpacing artık Java'da hesaplanmıyor, Shader'a sw ve visibleTraces gidiyor.
	    glUniform1f(gainLoc, gain[0]);
	    glUniform1i(sampleLoc, ns);
	    glUniform1f(hLoc, sh); 
	    glUniform1i(totalVisibleLoc, visibleTraces); // Shader'daki margin hesabı için
	    glUniform1f(screenWidthLoc, sw);             // Shader'daki margin hesabı için

	    // 5. Texture Bağla
	    glActiveTexture(GL_TEXTURE0);
	    glBindTexture(GL_TEXTURE_2D, textureId);
	    glUniform1i(glGetUniformLocation(shaderProgram, "seismicTex"), 0);

	    // 6. VAO ve Çizim
	    int vao = glGenVertexArrays();
	    glBindVertexArray(vao);

	    for (int i = 0; i < visibleTraces; i++) {
	        glUniform1i(traceLoc, i);
	        glDrawArrays(GL_LINE_STRIP, 0, ns);
	    }

	    glBindVertexArray(0);
	    glDeleteVertexArrays(vao);
	    glUseProgram(0);
	}
	 
	// Global Buffer'lar (Tekrar tekrar RAM ayırmamak için)
    private FloatBuffer globalFloatBuffer = null;
    private IntBuffer globalIntBuffer = null;
    private int currentFloatCapacity = 0;
    private int currentIntCapacity = 0;
	

	 public void wiggle4(int vboId, int iboId, int totalIndices, float sw, float sh) {
	     // 1. Shader'ı devre dışı bırak (Legacy Mode)
	     glUseProgram(0);
	     glViewport(0, 0, (int)sw, (int)sh);

	     // 2. Matris Ayarları (Görüntüyü ekrana sığdırır)
	     glMatrixMode(GL_PROJECTION);
	     glPushMatrix();
	     glLoadIdentity();
	     glOrtho(0, sw, sh, 0, -1, 1);
	     
	     glMatrixMode(GL_MODELVIEW);
	     glPushMatrix();
	     glLoadIdentity();

	     // 3. Primitive Restart Özelliğini Aç (Zikzakları önleyen sihirli anahtar)
	     // Bu komut IBO içindeki 0xFFFFFFFF değerini görünce kalemi kağıttan kaldırır.
	     glEnable(GL_PRIMITIVE_RESTART);
	     glPrimitiveRestartIndex(0xFFFFFFFF);

	     // 4. Buffer'ları Bağla ve Formatı Tanımla
	     glBindBuffer(GL_ARRAY_BUFFER, vboId);
	     glEnableClientState(GL_VERTEX_ARRAY);
	     // 2: x ve y koordinatları, GL_FLOAT: veri tipi, 0: boşluk yok, 0: baştan başla
	     glVertexPointer(2, GL_FLOAT, 0, 0);

	     // 5. IBO'yu bağla (Çizim sırası ve restart işaretleri)
	     glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);

	     // 6. TEK BİR EMİRLE TÜM KESİTİ ÇİZ
	     glColor3f(0.0f, 0.0f, 0.0f); // Siyah Wiggle
	     glDrawElements(GL_LINE_STRIP, totalIndices, GL_UNSIGNED_INT, 0);

	     // 7. Temizlik ve Ayarları Geri Yükle
	     glDisableClientState(GL_VERTEX_ARRAY);
	     glDisable(GL_PRIMITIVE_RESTART);
	     glBindBuffer(GL_ARRAY_BUFFER, 0);
	     glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
	     
	     glPopMatrix();
	     glMatrixMode(GL_PROJECTION);
	     glPopMatrix();
	 }
	 
	 
	 
	 
	 public int uploadSeismicToGPU(float[] data, int ns, int ntr) {
		// 1. GPU üzerinde yeni bir doku (texture) kimliği oluştur
		    int texId = glGenTextures();
		    glBindTexture(GL_TEXTURE_2D, texId);

		    // 2. Veriyi Java heap belleğinden yerel (native) belleğe kopyala
		    // OpenGL veriyi doğrudan Java dizisinden okuyamaz, Buffer gereklidir.
		    FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
		    buffer.put(data);
		    buffer.flip();

		    // 3. Texture parametrelerini ayarla (Sismik veri için doğrusal filtreleme)
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		    
		    // Verinin kenarlarda tekrar etmesini engelle (Artifact önleyici)
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		    // 4. Veriyi GPU belleğine transfer et
		    // GL_R32F: Her bir sismik örneği 32-bit float olarak "Red" kanalında saklar.
		    // ntr: Genişlik (İz sayısı), ns: Yükseklik (Örnek sayısı)
		    glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, ns, ntr, 0, GL_RED, GL_FLOAT, buffer);

		    // 5. Bağlantıyı kes (Güvenlik için)
		    glBindTexture(GL_TEXTURE_2D, 0);

		    return texId;
	    }
	 
	 
	  public int initLineShaders() {
	        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
	        glShaderSource(vertexShader, String.join("", WIGGLE_LINE_VERTEX_SHADER));
	        glCompileShader(vertexShader);

	        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
	        glShaderSource(fragmentShader, String.join("", WIGGLE_LINE_FRAGMENT_SHADER));
	        glCompileShader(fragmentShader);

	       int shaderProgram = glCreateProgram();
	        glAttachShader(shaderProgram, vertexShader);
	        glAttachShader(shaderProgram, fragmentShader);
	        glLinkProgram(shaderProgram);

	        glDeleteShader(vertexShader);
	        glDeleteShader(fragmentShader);
	        
	        return shaderProgram;
	    }
	 
	 
	 
	  public final String[] WIGGLE_LINE_VERTEX_SHADER = {
			    "#version 330 core\n",
			    "uniform sampler2D seismicTex;\n",
			    "uniform int numSamples;\n",
			    "uniform int currentTrace;\n",
			    "uniform int totalVisibleTraces;\n", // YENİ: Toplam görünür iz sayısı
			    "uniform float wiggleGain;\n",
			    "uniform float screenWidth;\n",    // YENİ: Ekran genişliği
			    "uniform float screenHeight;\n",
			    "uniform mat4 projection;\n",
			    "void main() {\n",
			    "    int sIdx = gl_VertexID;\n",
			    "\n",
			    "    // 1. Kenar Boşluğu (Margin) ve İz Aralığı Hesabı\n",
			    "    float margin = screenWidth / float(totalVisibleTraces + 1);\n",
			    "    float dynamicXSpacing = (screenWidth - (2.0 * margin)) / max(1.0, float(totalVisibleTraces - 1));\n",
			    "\n",
			    "    // 2. Texture Koordinatları\n",
			    "    float u = float(sIdx) / float(numSamples - 1);\n",
			    "    float v = float(currentTrace) / float(textureSize(seismicTex, 0).y - 1);\n",
			    "    float amplitude = texture(seismicTex, vec2(u, v)).r;\n",
			    "\n",
			    "    // 3. X Pozisyonu (Margin eklenmiş hali)\n",
			    "    float xAnchor = margin + (float(currentTrace) * dynamicXSpacing);\n",
			    "    float xPos = xAnchor + (amplitude * wiggleGain);\n",
			    "\n",
			    "    // 4. Y Pozisyonu\n",
			    "    float yPos = float(sIdx) * (screenHeight / float(numSamples - 1));\n",
			    "\n",
			    "    gl_Position = projection * vec4(xPos, yPos, 0.0, 1.0);\n",
			    "}\n"
			};
	 
	 public final String[] WIGGLE_LINE_FRAGMENT_SHADER = {
		        "#version 330 core\n",
		        "out vec4 FragColor;\n",
		        "uniform vec4 lineColor;\n",
		        "void main() { FragColor = lineColor; }\n"
		    };
	 
	 
	// Global Java Array'leri (Sürekli 'new' yapmamak için)
	    private float[] globalVertices = null;
	    private int[] globalIndices = null;
	 
	    public int[] createVBO(float[] allData, int ntr, int ns, float sw, float sh, float gain) {
	        int requiredVerticesLength = ntr * ns * 2;
	        int requiredIndicesLength = ntr * (ns + 1);

	        // 1. Array'leri SADECE yetersizse yeniden oluştur (Heap bellek sızıntısını önler)
	        if (globalVertices == null || globalVertices.length < requiredVerticesLength) {
	            globalVertices = new float[requiredVerticesLength];
	        }
	        if (globalIndices == null || globalIndices.length < requiredIndicesLength) {
	            globalIndices = new int[requiredIndicesLength];
	        }

	        int restartIndex = 0xFFFFFFFF; // Durma işareti
	        
	        float margin = sw / (float)(ntr + 1); 
	        float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, ntr - 1);
	        float ySpacing = sh / (float)(ns - 1);

	        int vIdx = 0;
	        int iIdx = 0;

	        for (int i = 0; i < ntr; i++) {
	            float xAnchor = margin + (i * dynamicTraceSpacing);
	            
	            for (int j = 0; j < ns; j++) {
	                float amplitude = allData[i * ns + j];
	                
	                // Yeniden yaratmak yerine mevcut dizinin üzerine yazıyoruz
	                globalVertices[vIdx++] = xAnchor + (amplitude * gain); 
	                globalVertices[vIdx++] = j * ySpacing;                
	                
	                globalIndices[iIdx++] = (i * ns) + j;
	            }
	            globalIndices[iIdx++] = restartIndex;
	        }

	        // DİKKAT: Artık array'in tamamını değil, sadece kullandığımız kısmını (requiredLength) gönderiyoruz
	        int vboId = uploadToGPU(globalVertices, requiredVerticesLength, GL_ARRAY_BUFFER);
	        int iboId = uploadToGPU(globalIndices, requiredIndicesLength, GL_ELEMENT_ARRAY_BUFFER);

	        return new int[]{vboId, iboId, requiredIndicesLength};
	    }
	 
	 // Float veriler (VBO) için
	    public int uploadToGPU(float[] data, int validLength, int target) {
	        int bufferId = glGenBuffers();
	        glBindBuffer(target, bufferId);
	        
	        if (globalFloatBuffer == null || currentFloatCapacity < validLength) {
	            currentFloatCapacity = validLength;
	            globalFloatBuffer = BufferUtils.createFloatBuffer(currentFloatCapacity);
	        }
	        
	        globalFloatBuffer.clear();
	        // Sadece dolu kısmı buffer'a koy
	        globalFloatBuffer.put(data, 0, validLength).flip(); 
	        
	        glBufferData(target, globalFloatBuffer, GL_STATIC_DRAW);
	        glBindBuffer(target, 0); 
	        return bufferId;
	    }

	    // Integer veriler (IBO/Index) için
	    public int uploadToGPU(int[] data, int validLength, int target) {
	        int bufferId = glGenBuffers();
	        glBindBuffer(target, bufferId);
	        
	        if (globalIntBuffer == null || currentIntCapacity < validLength) {
	            currentIntCapacity = validLength;
	            globalIntBuffer = BufferUtils.createIntBuffer(currentIntCapacity);
	        }
	        
	        globalIntBuffer.clear();
	        // Sadece dolu kısmı buffer'a koy
	        globalIntBuffer.put(data, 0, validLength).flip(); 
	        
	        glBufferData(target, globalIntBuffer, GL_STATIC_DRAW);
	        glBindBuffer(target, 0);
	        return bufferId;
	    }
	   
	 

}

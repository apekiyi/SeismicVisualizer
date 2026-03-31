import static org.lwjgl.opengl.GL11.GL_POLYGON;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor3f;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;

import imgui.ImGui;

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

public class VariableArea {
	
	public void va1(int not, float[] data, float[] gain, int ns, int visible) {
	    var drawList = ImGui.getBackgroundDrawList();
	    
	    float screenWidth = ImGui.getIO().getDisplaySizeX();
	    float screenHeight = ImGui.getIO().getDisplaySizeY();

	    int visibleTraces = Math.min(not, visible); 
	    float margin = screenWidth / (visibleTraces + 1); 
	    float dynamicTraceSpacing = (screenWidth - (2 * margin)) / Math.max(1, visibleTraces - 1);
	    float dynamicSampleSpacing = screenHeight / (ns > 0 ? ns : 1);
	    float currentGain = gain[0];

	    // Dolgu Rengi: Koyu Gri / Siyah tonu
	    int fillColor = ImGui.getColorU32(0.1f, 0.1f, 0.1f, 1.0f);

	    for (int i = 0; i < visibleTraces; i++) {
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        
	        for (int j = 0; j < ns - 1; j++) {
	            int idx0 = i * ns + j;
	            int idx1 = i * ns + j + 1;
	            
	            float v0 = data[idx0];
	            float v1 = data[idx1];
	            
	            float y0 = j * dynamicSampleSpacing;
	            float y1 = (j + 1) * dynamicSampleSpacing;
	            
	            float px0 = xAnchor + (v0 * currentGain);
	            float px1 = xAnchor + (v1 * currentGain);

	            // 1. DURUM: Segmentin başı da sonu da pozitif (Trapez/Dörtgen Dolgusu)
	            if (v0 > 0 && v1 > 0) {
	                // Sırasıyla: Sol Üst (X,Y), Sağ Üst (X,Y), Sağ Alt (X,Y), Sol Alt (X,Y)
	                drawList.addQuadFilled(
	                    xAnchor, y0,  
	                    px0, y0,      
	                    px1, y1,      
	                    xAnchor, y1,  
	                    fillColor
	                );
	            } 
	            // 2. DURUM: Negatiften pozitife geçiş (Sıfır geçiş noktasından başlayan Üçgen)
	            else if (v0 <= 0 && v1 > 0) {
	                float fraction = Math.abs(v0) / (Math.abs(v0) + Math.abs(v1));
	                float yZero = y0 + (fraction * (y1 - y0));
	                
	                // Sırasıyla: Sıfır Noktası (X,Y), Sağ Alt (X,Y), Sol Alt (X,Y)
	                drawList.addTriangleFilled(
	                    xAnchor, yZero, 
	                    px1, y1,        
	                    xAnchor, y1,    
	                    fillColor
	                );
	            } 
	            // 3. DURUM: Pozitiften negatife geçiş (Sıfır geçiş noktasında biten Üçgen)
	            else if (v0 > 0 && v1 <= 0) {
	                float fraction = Math.abs(v0) / (Math.abs(v0) + Math.abs(v1));
	                float yZero = y0 + (fraction * (y1 - y0));
	                
	                // Sırasıyla: Sol Üst (X,Y), Sağ Üst (X,Y), Sıfır Noktası (X,Y)
	                drawList.addTriangleFilled(
	                    xAnchor, y0,  
	                    px0, y0,      
	                    xAnchor, yZero, 
	                    fillColor
	                );
	            }
	        }
	    }
	}
	
	public void va2(int not, int visible, float[] gain, int ns, float[] data) {
		
		glUseProgram(0);
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

	    // 2. Dinamik Spacing ve Kenar Boşluğu Hesaplaması (wiggle2 ile birebir aynı)
	    int visibleTraces = Math.min(not, visible); 
	    float margin = sw / (visibleTraces + 1); 
	    float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, visibleTraces - 1);
	    float dynamicSampleSpacing = sh / (ns > 0 ? ns : 1);
	    float currentGain = gain[0];

	    // Renk Seçimi: İsteğe bağlı, orijinal kodundaki gibi sarımsı renk (veya siyah 0f, 0f, 0f)
	    glColor3f(1.0f, 1.0f, 0.1f); 

	    // --- VA Çizim Döngüsü ---
	    for (int i = 0; i < visibleTraces; i++) {
	        // xAnchor: Wiggle'ın tam ortası (Referans/Sıfır hattı)
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        boolean inPolygon = false;

	        for (int j = 0; j < ns - 1; j++) {
	            // Tek boyutlu (1D) array için index hesaplaması
	            int idx0 = i * ns + j;
	            int idx1 = i * ns + j + 1;
	            
	            float v0 = data[idx0];
	            float v1 = data[idx1];
	            
	            // Y koordinatları (wiggle2 ile birebir aynı olması için 0.5f kaydırması çıkarıldı)
	            float y0 = j * dynamicSampleSpacing;
	            float y1 = (j + 1) * dynamicSampleSpacing;

	            // 1. DURUM: Pozitif bölge başlangıcı (Sıfır Geçişi veya Başlangıç)
	            if (!inPolygon && v0 > 0) {
	                glBegin(GL_POLYGON);
	                glVertex2f(xAnchor, y0); // Taban çizgisi
	                glVertex2f(xAnchor + (v0 * currentGain), y0); // Genlik sapması
	                inPolygon = true;
	            } 
	            // 2. DURUM: Negatiften pozitife geçiş (Sıfır Geçiş Noktasını Bul)
	            else if (!inPolygon && v0 <= 0 && v1 > 0) {
	                float fraction = Math.abs(v0) / (Math.abs(v0) + Math.abs(v1));
	                float yZero = y0 + (fraction * (y1 - y0));
	                
	                glBegin(GL_POLYGON);
	                glVertex2f(xAnchor, yZero); // Sıfır noktasından başlat
	                inPolygon = true;
	            }

	            // 3. DURUM: Pozitif bölge içerisinde ilerleme
	            if (inPolygon && v1 > 0) {
	                glVertex2f(xAnchor + (v1 * currentGain), y1);
	            }

	            // 4. DURUM: Pozitiften negatife geçiş (Sıfır Geçişi - Poligon Biter)
	            if (inPolygon && v1 <= 0) {
	                float fraction = Math.abs(v0) / (Math.abs(v0) + Math.abs(v1));
	                float yZero = y0 + (fraction * (y1 - y0));
	                
	                glVertex2f(xAnchor, yZero); // Sıfır noktasına in
	                glVertex2f(xAnchor, yZero); // Taban çizgisine dön
	                glEnd();
	                inPolygon = false;
	            } 
	            // 5. DURUM: Sismik izin sonuna geldik ve hala pozitifsek poligonu kapat
	            else if (inPolygon && j == ns - 2) {
	                glVertex2f(xAnchor, y1); // Doğrudan taban çizgisine in
	                glEnd();
	                inPolygon = false;
	            }
	        }
	    }

	    // 3. Ayarları geri yükle
	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	    glPopAttrib();
	}
	
	
	
	public void va3(int vaShaderProgram, int textureId, float max, int ns, float[] gain, int ntr, int visible) {
	    if (vaShaderProgram <= 0 || textureId <= 0) return;

	    glUseProgram(vaShaderProgram);

	    // 1. Ekran Boyutlarını Al
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();
	    int visibleTraces = Math.min(ntr, visible);
	    glViewport(0, 0, (int)sw, (int)sh);

	    // 2. Uniform Lokasyonlarını Bul
	    int projLoc = glGetUniformLocation(vaShaderProgram, "projection");
	    int gainLoc = glGetUniformLocation(vaShaderProgram, "wiggleGain");
	    int traceLoc = glGetUniformLocation(vaShaderProgram, "currentTrace");
	    int sampleLoc = glGetUniformLocation(vaShaderProgram, "numSamples");
	    int hLoc = glGetUniformLocation(vaShaderProgram, "screenHeight");
	    int totalVisibleLoc = glGetUniformLocation(vaShaderProgram, "totalVisibleTraces");
	    int screenWidthLoc = glGetUniformLocation(vaShaderProgram, "screenWidth");
	    int colorLoc = glGetUniformLocation(vaShaderProgram, "fillColor"); // YENİ: Dolgu Rengi

	    // 3. Projeksiyon Matrisi (Ortho)
	    float[] orthoMatrix = {
	        2.0f/sw,  0,        0,    0,
	        0,       -2.0f/sh,  0,    0,
	        0,        0,       -1.0f, 0,
	       -1.0f,     1.0f,     0,    1.0f
	    };
	    glUniformMatrix4fv(projLoc, false, orthoMatrix);

	    // 4. Uniform Değerlerini Güncelle
	    glUniform1f(gainLoc, gain[0]);
	    glUniform1i(sampleLoc, ns);
	    glUniform1f(hLoc, sh); 
	    glUniform1i(totalVisibleLoc, visibleTraces); 
	    glUniform1f(screenWidthLoc, sw);             
	    
	    // Dolgu Rengi: Koyu Gri/Siyah (R, G, B, Alpha)
	    glUniform4f(colorLoc, 0.1f, 0.1f, 0.1f, 1.0f);

	    // 5. Texture Bağla
	    glActiveTexture(GL_TEXTURE0);
	    glBindTexture(GL_TEXTURE_2D, textureId);
	    glUniform1i(glGetUniformLocation(vaShaderProgram, "seismicTex"), 0);

	    // DİKKAT: Üçgen şeritlerinin arka yüzeylerinin (negative genlikte ters dönen yüzeyler) 
	    // silinmemesi için Culling'i kapatıyoruz.
	    glDisable(GL_CULL_FACE); 

	    // 6. VAO ve Çizim
	    int vao = glGenVertexArrays();
	    glBindVertexArray(vao);

	    for (int i = 0; i < visibleTraces; i++) {
	        glUniform1i(traceLoc, i);
	        // BÜYÜK DEĞİŞİKLİK: Çizgi yerine Üçgen Şeridi kullanıyoruz
	        // ve her örneklem için 2 nokta (Sıfır Hattı + Genlik) olduğu için 'ns * 2' gönderiyoruz.
	        glDrawArrays(GL_TRIANGLE_STRIP, 0, ns * 2);
	    }

	    glBindVertexArray(0);
	    glDeleteVertexArrays(vao);
	    glUseProgram(0);
	}
	
	// Global Buffer'lar (Native bellek sızıntısını önlemek için)
    private FloatBuffer globalFloatBuffer = null;
    private IntBuffer globalIntBuffer = null;
    private int currentFloatCapacity = 0;
    private int currentIntCapacity = 0;

    // Global Java Array'leri (Heap bellek sızıntısını önlemek için)
    private float[] globalVertices = null;
    private int[] globalIndices = null;
	
	public void va4(int vaVboId, int vaIboId, int totalIndices, float sw, float sh) {
	    glUseProgram(0);
	    glViewport(0, 0, (int)sw, (int)sh);

	    glMatrixMode(GL_PROJECTION);
	    glPushMatrix();
	    glLoadIdentity();
	    glOrtho(0, sw, sh, 0, -1, 1);
	    
	    glMatrixMode(GL_MODELVIEW);
	    glPushMatrix();
	    glLoadIdentity();

	    // Siyah/Koyu Gri Dolgu Rengi
	    glColor3f(0.1f, 0.1f, 0.1f); 

	    // Triangle Strip'i koparacak Restart işaretini aktifleştir
	    glEnable(GL_PRIMITIVE_RESTART);
	    glPrimitiveRestartIndex(0xFFFFFFFF);

	    // Buffer'ları Bağla
	    glBindBuffer(GL_ARRAY_BUFFER, vaVboId);
	    glEnableClientState(GL_VERTEX_ARRAY);
	    glVertexPointer(2, GL_FLOAT, 0, 0);

	    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vaIboId);

	    // TEK EMİRLE TÜM DOLGULARI ŞERİT HALİNDE ÇİZ
	    glDrawElements(GL_TRIANGLE_STRIP, totalIndices, GL_UNSIGNED_INT, 0);

	    // Temizlik
	    glDisableClientState(GL_VERTEX_ARRAY);
	    glDisable(GL_PRIMITIVE_RESTART);
	    glBindBuffer(GL_ARRAY_BUFFER, 0);
	    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
	    
	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	}
	
	  public int initVaShaders() {
	        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
	        glShaderSource(vertexShader, String.join("", vaVertexShaderSource));
	        glCompileShader(vertexShader);

	        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
	        glShaderSource(fragmentShader, String.join("", vaFragmentShaderSource));
	        glCompileShader(fragmentShader);

	       int shaderProgram = glCreateProgram();
	        glAttachShader(shaderProgram, vertexShader);
	        glAttachShader(shaderProgram, fragmentShader);
	        glLinkProgram(shaderProgram);

	        glDeleteShader(vertexShader);
	        glDeleteShader(fragmentShader);
	        
	        return shaderProgram;
	    }
	
	
	String[] vaVertexShaderSource = new String[] {
		    "#version 330 core\n",
		    "\n",
		    "uniform mat4 projection;\n",
		    "uniform float wiggleGain;\n",
		    "uniform int currentTrace;\n",
		    "uniform int numSamples;\n",
		    "uniform float screenHeight;\n",
		    "uniform int totalVisibleTraces;\n",
		    "uniform float screenWidth;\n",
		    "\n",
		    "uniform sampler2D seismicTex;\n",
		    "\n",
		    "out float v_amp;\n",
		    "\n",
		    "void main() {\n",
		    "    // Çift sayılar sıfır eksenini (taban), tek sayılar veri genliğini (tepe) temsil eder\n",
		    "    int sampleIdx = gl_VertexID / 2;\n",
		    "    int side = gl_VertexID % 2;\n",
		    "\n",
		    "    // X Ekseninde Margin ve Spacing Hesaplamaları\n",
		    "    float margin = screenWidth / float(totalVisibleTraces + 1);\n",
		    "    float traceSpacing = 0.0;\n",
		    "    if (totalVisibleTraces > 1) {\n",
		    "        traceSpacing = (screenWidth - (2.0 * margin)) / float(totalVisibleTraces - 1);\n",
		    "    }\n",
		    "    float xAnchor = margin + float(currentTrace) * traceSpacing;\n",
		    "\n",
		    "    // Y Ekseninde Sample Spacing Hesaplaması\n",
		    "    float sampleSpacing = screenHeight / float(numSamples > 0 ? numSamples : 1);\n",
		    "    float yPos = float(sampleIdx) * sampleSpacing;\n",
		    "\n",
		    "    // Texture'dan genliği oku (X: currentTrace, Y: sampleIdx)\n",
		    "    float amp = texelFetch(seismicTex, ivec2(sampleIdx, currentTrace), 0).r;\n",
		    "\n",
		    "    // Fragment Shader'da discard (çöpe atma) işlemi yapabilmek için genliği gönderiyoruz\n",
		    "    v_amp = amp;\n",
		    "\n",
		    "    float xPos = xAnchor;\n",
		    "    // Eğer 'side == 1' ise noktayı genlik kadar sağa/sola kaydır\n",
		    "    if (side == 1) {\n",
		    "        xPos += amp * wiggleGain;\n",
		    "    }\n",
		    "\n",
		    "    gl_Position = projection * vec4(xPos, yPos, 0.0, 1.0);\n",
		    "}\n"
		};
	
	String[] vaFragmentShaderSource = new String[] {
		    "#version 330 core\n",
		    "\n",
		    "in float v_amp;\n",
		    "out vec4 FragColor;\n",
		    "\n",
		    "uniform vec4 fillColor;\n",
		    "\n",
		    "void main() {\n",
		    "    // Mükemmel sıfır-geçiş (zero-crossing) hilesi:\n",
		    "    // Eğer genlik sıfır veya negatifse, bu pikseli poligonun içine dahil etme.\n",
		    "    if (v_amp <= 0.0) {\n",
		    "        discard;\n",
		    "    }\n",
		    "    \n",
		    "    FragColor = fillColor;\n",
		    "}\n"
		};
	
	
	public int[] createVaVBO(float[] allData, int ntr, int ns, float sw, float sh, float gain) {
	    // 1. Maksimum Kapasite Tahmini
	    int maxVerticesLength = ntr * ns * 8; 
	    int maxIndicesLength = ntr * ns * 4;
	    
	    // Array'leri SADECE kapasite yetersizse yeniden oluştur (Sıfır atık)
	    if (globalVertices == null || globalVertices.length < maxVerticesLength) {
	        globalVertices = new float[maxVerticesLength];
	    }
	    if (globalIndices == null || globalIndices.length < maxIndicesLength) {
	        globalIndices = new int[maxIndicesLength];
	    }
	    
	    int vertexCount = 0; // Toplam üretilen nokta (vertex) sayısı
	    int iIdx = 0;        // Index array sayacı
	    int restartIndex = 0xFFFFFFFF; // Şeridi koparma işareti

	    float margin = sw / (float)(ntr + 1); 
	    float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, ntr - 1);
	    float ySpacing = sh / (float)(ns - 1);

	    for (int i = 0; i < ntr; i++) {
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        boolean inPositiveLobe = false;

	        for (int j = 0; j < ns - 1; j++) {
	            float v0 = allData[i * ns + j];
	            float v1 = allData[i * ns + j + 1];

	            float y0 = j * ySpacing;
	            float y1 = (j + 1) * ySpacing;

	            float px0 = xAnchor + (v0 * gain);
	            float px1 = xAnchor + (v1 * gain);

	            // DURUM 1: Lob Başlangıcı
	            if (j == 0 && v0 > 0) {
	                globalVertices[vertexCount * 2] = xAnchor; globalVertices[vertexCount * 2 + 1] = y0;
	                globalIndices[iIdx++] = vertexCount++;
	                
	                globalVertices[vertexCount * 2] = px0;     globalVertices[vertexCount * 2 + 1] = y0;
	                globalIndices[iIdx++] = vertexCount++;
	                inPositiveLobe = true;
	            }

	            // DURUM 2: Negatiften Pozitife Geçiş (Sıfır Geçişi)
	            if (!inPositiveLobe && v0 <= 0 && v1 > 0) {
	                float fraction = Math.abs(v0) / (Math.abs(v0) + Math.abs(v1));
	                float yZero = y0 + (fraction * (y1 - y0));

	                globalVertices[vertexCount * 2] = xAnchor; globalVertices[vertexCount * 2 + 1] = yZero;
	                globalIndices[iIdx++] = vertexCount++; 
	                
	                globalVertices[vertexCount * 2] = xAnchor; globalVertices[vertexCount * 2 + 1] = yZero;
	                globalIndices[iIdx++] = vertexCount++; 

	                inPositiveLobe = true;
	            }

	            // DURUM 3: Pozitif Bölgede İlerleme
	            if (inPositiveLobe && v1 > 0) {
	                globalVertices[vertexCount * 2] = xAnchor; globalVertices[vertexCount * 2 + 1] = y1;
	                globalIndices[iIdx++] = vertexCount++;
	                
	                globalVertices[vertexCount * 2] = px1;     globalVertices[vertexCount * 2 + 1] = y1;
	                globalIndices[iIdx++] = vertexCount++;
	            }

	            // DURUM 4: Pozitiften Negatife Geçiş
	            if (inPositiveLobe && v1 <= 0) {
	                float fraction = Math.abs(v0) / (Math.abs(v0) + Math.abs(v1));
	                float yZero = y0 + (fraction * (y1 - y0));

	                globalVertices[vertexCount * 2] = xAnchor; globalVertices[vertexCount * 2 + 1] = yZero;
	                globalIndices[iIdx++] = vertexCount++; 
	                
	                globalVertices[vertexCount * 2] = xAnchor; globalVertices[vertexCount * 2 + 1] = yZero;
	                globalIndices[iIdx++] = vertexCount++; 

	                globalIndices[iIdx++] = restartIndex; // ŞERİDİ KOPAR!
	                inPositiveLobe = false;
	            }

	            // DURUM 5: İzin sonuna geldik
	            if (inPositiveLobe && j == ns - 2) {
	                globalIndices[iIdx++] = restartIndex; // ŞERİDİ KOPAR!
	                inPositiveLobe = false;
	            }
	        }
	    }

	    // 2. GPU'ya SADECE Dolu Olan Kısımları Yükle
	    int validVerticesLength = vertexCount * 2;
	    int validIndicesLength = iIdx;

	    int vboId = uploadToGPU(globalVertices, validVerticesLength, GL_ARRAY_BUFFER);
	    int iboId = uploadToGPU(globalIndices, validIndicesLength, GL_ELEMENT_ARRAY_BUFFER);

	    // VBO ID, IBO ID ve Toplam Index sayısını dön
	    return new int[]{vboId, iboId, validIndicesLength};
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
		    // Sadece dizinin dolu olan kısmını GPU'ya yolla
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
		    // Sadece dizinin dolu olan kısmını GPU'ya yolla
		    globalIntBuffer.put(data, 0, validLength).flip();
		    
		    glBufferData(target, globalIntBuffer, GL_STATIC_DRAW);
		    glBindBuffer(target, 0);
		    return bufferId;
		}
	
	
	

}

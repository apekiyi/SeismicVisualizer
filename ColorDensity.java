import imgui.ImGui;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

public class ColorDensity {
	
	   int[][] rawColors = {
		        {0, 0, 65535}, {7259, 7259, 64603}, {14520, 14520, 63670}, {21779, 21779, 62740},
		        {29040, 29040, 61808}, {36301, 36301, 60875}, {43560, 43560, 59944}, {50821, 50821, 59013},
		        {58082, 58082, 58082}, {61808, 61808, 61808}, {65535, 65535, 65535}, {61808, 61808, 61808},
		        {58082, 58082, 58082}, {59013, 50821, 50821}, {59944, 43560, 43560}, {60875, 36301, 36301},
		        {61808, 29040, 29040}, {62740, 21779, 21779}, {63670, 14520, 14520}, {64603, 7259, 7259},
		        {65535, 0, 0}
		    };

	
	public int[] densitypalette() {
	    // Paylaştığın 65535 formatlı 21 satırlık redblue RGB değerleri
		int[] density=new int[rawColors.length];

	    // Değerleri 0.0f - 1.0f arasına çekip ImGui renk formatına dönüştürüyoruz
	    for (int i = 0; i < 21; i++) {
	        float r = rawColors[i][0] / 65535.0f;
	        float g = rawColors[i][1] / 65535.0f;
	        float b = rawColors[i][2] / 65535.0f;
	        density[i] = ImGui.getColorU32(r, g, b, 1.0f);
	    }
		return density;
	}
	
	public void density1(int not, float[] data, float[] gain, int ns, int visible, float max,int[] density) {
	    var drawList = ImGui.getBackgroundDrawList();
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();

	    int visibleTraces = Math.min(not, visible); 
	    
	    // YENİ MANTIK: Ekranı tam olarak iz ve örneklem sayısına böl (Sıfır boşluk)
	    float cellWidth = sw / (float) Math.max(1, visibleTraces);
	    float cellHeight = sh / (float) Math.max(1, ns);
	    
	    float currentGain = gain[0];
	    
	    // Sıfıra bölünme hatasını engellemek için güvenli max değeri
	    float safeMax = (max <= 0.0001f) ? 1.0f : max;

	    for (int i = 0; i < visibleTraces; i++) {
	        // İlgili hücrenin tam sol ve sağ sınırları
	        float xLeft = i * cellWidth;
	        float xRight = xLeft + cellWidth;

	        for (int j = 0; j < ns; j++) {
	            float val = data[i * ns + j] * currentGain;
	            
	            // 1. Normalizasyon: Değeri -max ile +max arasından alıp 0.0 ile 1.0 arasına çek
	            float norm = (val + safeMax) / (2.0f * safeMax);
	            
	            // 2. Taşmaları engelle (Clamp)
	            if (norm < 0.0f) norm = 0.0f;
	            if (norm > 1.0f) norm = 1.0f;
	            
	            // 3. 0.0-1.0 aralığını 0-20 (ncolors=21) indeks aralığına çevir
	            int colorIndex = (int) (norm * 20.0f);
	            
	            // İlgili hücrenin tam üst ve alt sınırları
	            float yTop = j * cellHeight;
	            float yBottom = yTop + cellHeight;
	            
	            // ImGui ile hücreyi ekrana çiz
	            drawList.addRectFilled(xLeft, yTop, xRight, yBottom, density[colorIndex]);
	        }
	    }
	}
	
	public float[][] initGLPalette()
	{
		float[][] glPalette=new float[rawColors.length][3];
		for (int i = 0; i < 21; i++) {
	        glPalette[i][0] = rawColors[i][0] / 65535.0f; // Red
	        glPalette[i][1] = rawColors[i][1] / 65535.0f; // Green
	        glPalette[i][2] = rawColors[i][2] / 65535.0f; // Blue
		}
		return glPalette;
	}
	
	
	public void density2(int not, float[] data, float[] gain, int ns, int visible, float max,float[][] glPalette) {
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();

	    // 1. Önceki tüm Shader etkilerini kapat (Saf Legacy Mod)
	    glUseProgram(0); 
	    glViewport(0, 0, (int)sw, (int)sh);

	    // 2. 2D Kamera Ayarları
	    glMatrixMode(GL_PROJECTION);
	    glPushMatrix();
	    glLoadIdentity();
	    glOrtho(0, sw, sh, 0, -1, 1);
	    
	    glMatrixMode(GL_MODELVIEW);
	    glPushMatrix();
	    glLoadIdentity();

	    // 3. Hücre Boyutlarını Hesapla (Boşluksuz)
	    int visibleTraces = Math.min(not, visible);
	    float cellWidth = sw / (float) Math.max(1, visibleTraces);
	    float cellHeight = sh / (float) Math.max(1, ns);
	    float currentGain = gain[0];
	    float safeMax = (max <= 0.0001f) ? 1.0f : max;

	    // 4. ÇİZİM DÖNGÜSÜ (Eski usul glBegin - glEnd)
	    glBegin(GL_QUADS);
	    for (int i = 0; i < visibleTraces; i++) {
	        float xLeft = i * cellWidth;
	        float xRight = xLeft + cellWidth;

	        for (int j = 0; j < ns; j++) {
	            float val = data[i * ns + j] * currentGain;
	            
	            // Genliği normalize et (0.0 ile 1.0 arasına çek)
	            float norm = (val + safeMax) / (2.0f * safeMax);
	            if (norm < 0.0f) norm = 0.0f;
	            if (norm > 1.0f) norm = 1.0f;

	            // İlgili rengi seç ve fırçaya sür
	            int colorIndex = (int) (norm * 20.0f);
	            glColor3f(glPalette[colorIndex][0], glPalette[colorIndex][1], glPalette[colorIndex][2]);

	            // Dörtgenin alt ve üst sınırları
	            float yTop = j * cellHeight;
	            float yBottom = yTop + cellHeight;

	            // Dört köşeyi saat yönünün tersine (veya saat yönünde) tanımla
	            glVertex2f(xLeft, yTop);    // Sol Üst
	            glVertex2f(xRight, yTop);   // Sağ Üst
	            glVertex2f(xRight, yBottom);// Sağ Alt
	            glVertex2f(xLeft, yBottom); // Sol Alt
	        }
	    }
	    glEnd();

	    // 5. Ayarları Geri Yükle
	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	}
	
	

	// Yeni parametreler eklendi: float totalTraces, float startTrace
	public void density3(int shaderProgram, int seismicTexId, int colormapTexId, float max, float[] gain, int visible, float totalTraces) {
	    if (shaderProgram <= 0 || seismicTexId <= 0 || colormapTexId <= 0) return;
	    
	    float startTrace=0;
	    glUseProgram(shaderProgram);
	    
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();
	    
	    glViewport(0, 0, (int)sw, (int)sh);

	    // Uniform Lokasyonları
	    int projLoc = glGetUniformLocation(shaderProgram, "projection");
	    int swLoc = glGetUniformLocation(shaderProgram, "screenWidth");
	    int shLoc = glGetUniformLocation(shaderProgram, "screenHeight");
	    
	    // --- YENİ EKLENEN UNIFORM LOKASYONLARI ---
	    int traceLoc = glGetUniformLocation(shaderProgram, "totalVisibleTraces");
	    int totalTracesLoc = glGetUniformLocation(shaderProgram, "totalTraces");
	    int startTraceLoc = glGetUniformLocation(shaderProgram, "startTrace");
	    
	    int maxLoc = glGetUniformLocation(shaderProgram, "maxAmp");
	    int gainLoc = glGetUniformLocation(shaderProgram, "gain");

	    float[] orthoMatrix = {
	        2.0f/sw,  0,        0,    0,
	        0,       -2.0f/sh,  0,    0,
	        0,        0,       -1.0f, 0,
	       -1.0f,     1.0f,     0,    1.0f
	    };
	    glUniformMatrix4fv(projLoc, false, orthoMatrix);

	    glUniform1f(swLoc, sw);
	    glUniform1f(shLoc, sh);
	    glUniform1f(maxLoc, max);
	    glUniform1f(gainLoc, gain[0]);
	    
	    // --- YENİ DEĞERLERİ SHADER'A GÖNDERİYORUZ ---
	    glUniform1f(traceLoc, (float)visible); 
	    glUniform1f(totalTracesLoc, totalTraces);
	    glUniform1f(startTraceLoc, startTrace);

	    // Slot 0: Sismik Veri
	    glActiveTexture(GL_TEXTURE0);
	    glBindTexture(GL_TEXTURE_2D, seismicTexId);
	    glUniform1i(glGetUniformLocation(shaderProgram, "seismicTex"), 0);

	    // Slot 1: Renk Paleti (Red-Blue)
	    glActiveTexture(GL_TEXTURE1);
	    glBindTexture(GL_TEXTURE_2D, colormapTexId);
	    glUniform1i(glGetUniformLocation(shaderProgram, "colormapTex"), 1);

	    // Çizim: Sadece 4 köşe
	    int vao = glGenVertexArrays();
	    glBindVertexArray(vao);
	    
	    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); 

	    glBindVertexArray(0);
	    glDeleteVertexArrays(vao);
	    glUseProgram(0);
	}
	
	// Global Buffer'lar (Native bellek sızıntısını önlemek için)
    private FloatBuffer globalFloatBuffer = null;
    private IntBuffer globalIntBuffer = null; // ColorDensity'de int buffer pek kullanılmasa da standart için ekliyoruz
    private int currentFloatCapacity = 0;
    private int currentIntCapacity = 0;

    // Global Java Array'leri (Heap bellek sızıntısını önlemek için)
    private float[] globalVertices = null;
    private float[] globalColors = null; // VariableArea ve Wiggle'dan farklı olarak burada Color array'imiz var
	
	public void density4(int vertexVboId, int colorVboId, int totalVertices, float sw, float sh) {
	    if (vertexVboId <= 0 || colorVboId <= 0) return;

	    glUseProgram(0); // Saf Legacy OpenGL moduna geç
	    glViewport(0, 0, (int)sw, (int)sh);

	    glMatrixMode(GL_PROJECTION);
	    glPushMatrix();
	    glLoadIdentity();
	    glOrtho(0, sw, sh, 0, -1, 1);
	    
	    glMatrixMode(GL_MODELVIEW);
	    glPushMatrix();
	    glLoadIdentity();

	    // 1. Koordinat Buffer'ını Bağla
	    glBindBuffer(GL_ARRAY_BUFFER, vertexVboId);
	    glEnableClientState(GL_VERTEX_ARRAY);
	    glVertexPointer(2, GL_FLOAT, 0, 0);

	    // 2. Renk Buffer'ını Bağla
	    glBindBuffer(GL_ARRAY_BUFFER, colorVboId);
	    glEnableClientState(GL_COLOR_ARRAY);
	    glColorPointer(3, GL_FLOAT, 0, 0);

	    // 3. Tek Emirle Tüm Renk Haritasını Çiz
	    glDrawArrays(GL_QUADS, 0, totalVertices);

	    // 4. Temizlik
	    glDisableClientState(GL_COLOR_ARRAY);
	    glDisableClientState(GL_VERTEX_ARRAY);
	    glBindBuffer(GL_ARRAY_BUFFER, 0);
	    
	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	}
	
	
	private static final String[] DENSITY_VERTEX_SHADER = new String[] {
		    "#version 330 core\n",
		    "uniform mat4 projection;\n",
		    "uniform float screenWidth;\n",
		    "uniform float screenHeight;\n",
		    
		    // Parametreler
		    "uniform float totalVisibleTraces;\n", 
		    "uniform float totalTraces;\n",        
		    "uniform float startTrace;\n",         
		    
		    "out vec2 v_texCoord;\n", 
		    "void main() {\n",
		    "    vec2 pos[4] = vec2[4]( \n",
		    "        vec2(0.0, 0.0), vec2(screenWidth, 0.0),\n",
		    "        vec2(0.0, screenHeight), vec2(screenWidth, screenHeight)\n",
		    "    );\n",
		    "    \n",
		    "    vec2 rotatedUV[4] = vec2[4]( \n",
		    "        vec2(0.0, 1.0), vec2(0.0, 0.0),\n",
		    "        vec2(1.0, 1.0), vec2(1.0, 0.0)\n",
		    "    );\n",
		    "    \n",
		    "    vec2 uv = rotatedUV[gl_VertexID];\n",
		    "    \n",
		    "    // --- DÜZELTİLEN KISIM: UV.Y (YATAY EKSEN) ORANLANIYOR --- \n",
		    "    // rotatedUV dizisinde sol taraf 1.0, sağ taraf 0.0 değerini veriyor.\n",
		    "    // Soldan sağa düzgün okuması için önce (1.0 - uv.y) ile ters çevirip, sonra oranlıyoruz.\n",
		    "    float horizontalGradient = 1.0 - uv.y;\n",
		    "    uv.y = (horizontalGradient * totalVisibleTraces + startTrace) / totalTraces;\n",
		    "    \n",
		    "    v_texCoord = uv;\n",
		    "    gl_Position = projection * vec4(pos[gl_VertexID], 0.0, 1.0);\n",
		    "}\n"
		};

		private static final String[] DENSITY_FRAGMENT_SHADER = new String[] {
		    "#version 330 core\n",
		    "in vec2 v_texCoord;\n",
		    "out vec4 FragColor;\n",
		    "uniform sampler2D seismicTex;\n", // 0. Slot: Sismik Veri Matrisi
		    "uniform sampler2D colormapTex;\n",// 1. Slot: Renk Paleti (Red-Blue)
		    "uniform float maxAmp;\n",
		    "uniform float gain;\n",
		    "void main() {\n",
		    "    // 1. Sismik dokudan o anki pikselin genliğini oku\n",
		    "    float amp = texture(seismicTex, v_texCoord).r;\n",
		    "    \n",
		    "    // 2. Genliği normalize et: [-maxAmp, +maxAmp] aralığından [0.0, 1.0] aralığına çek\n",
		    "    float safeMax = max(maxAmp, 0.0001);\n",
		    "    float normAmp = ((amp * gain) + safeMax) / (2.0 * safeMax);\n",
		    "    \n",
		    "    // 3. Değeri sınırla (Taşmaları önle)\n",
		    "    normAmp = clamp(normAmp, 0.0, 1.0);\n",
		    "    \n",
		    "    // 4. Renk paletinden (colormapTex) bu değere karşılık gelen rengi çek\n",
		    "    // X ekseni normAmp (0.0-1.0), Y ekseni resmin ortası (0.5)\n",
		    "    vec3 finalColor = texture(colormapTex, vec2(normAmp, 0.5)).rgb;\n",
		    "    \n",
		    "    FragColor = vec4(finalColor, 1.0);\n",
		    "}\n"
		};
		
		public int initDensityShaders() {
		    int vertexShader = glCreateShader(GL_VERTEX_SHADER);
		    glShaderSource(vertexShader, String.join("", DENSITY_VERTEX_SHADER));
		    glCompileShader(vertexShader);

		    int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
		    glShaderSource(fragmentShader, String.join("", DENSITY_FRAGMENT_SHADER));
		    glCompileShader(fragmentShader);

		    int shaderProgram = glCreateProgram();
		    glAttachShader(shaderProgram, vertexShader);
		    glAttachShader(shaderProgram, fragmentShader);
		    glLinkProgram(shaderProgram);

		    glDeleteShader(vertexShader);
		    glDeleteShader(fragmentShader);

		    return shaderProgram;
		}
	
		public int initColormapTexture() {

		    // Dizinin uzunluğunu dinamik olarak alıyoruz (Şu an için 21)
		    int numColors = rawColors.length; 

		    // Buffer boyutunu da renk sayısına göre ayarlıyoruz (Her renk için R, G, B yani 3 byte)
		    ByteBuffer buffer = BufferUtils.createByteBuffer(numColors * 3);
		    
		    for (int i = 0; i < numColors; i++) {
		        buffer.put((byte) ((rawColors[i][0] * 255) / 65535));
		        buffer.put((byte) ((rawColors[i][1] * 255) / 65535));
		        buffer.put((byte) ((rawColors[i][2] * 255) / 65535));
		    }
		    buffer.flip();

		    int texId = glGenTextures();
		    glBindTexture(GL_TEXTURE_2D, texId);
		    
		    // Genişlik (width) parametresine artık 'numColors' veriyoruz!
		    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, numColors, 1, 0, GL_RGB, GL_UNSIGNED_BYTE, buffer);
		    
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		    return texId;
		}
		
		public int[] createDensityVBO(float[] allData, int ntr, int ns, float sw, float sh, float gain, float max, float[][] glPalette) {
		    // 1 dörtgen (Quad) = 4 köşe (Vertex)
		    int totalQuads = ntr * ns;
		    int totalVertices = totalQuads * 4;

		    int requiredVerticesLength = totalVertices * 2;
		    int requiredColorsLength = totalVertices * 3;

		    // 1. Array'leri SADECE kapasite yetersizse yeniden oluştur (Sıfır atık)
		    if (globalVertices == null || globalVertices.length < requiredVerticesLength) {
		        globalVertices = new float[requiredVerticesLength];
		    }
		    if (globalColors == null || globalColors.length < requiredColorsLength) {
		        globalColors = new float[requiredColorsLength];
		    }

		    float cellWidth = sw / (float) Math.max(1, ntr);
		    float cellHeight = sh / (float) Math.max(1, ns);
		    float safeMax = (max <= 0.0001f) ? 1.0f : max;

		    int vIdx = 0; // Vertex Index
		    int cIdx = 0; // Color Index

		    for (int i = 0; i < ntr; i++) {
		        float xLeft = i * cellWidth;
		        float xRight = xLeft + cellWidth;

		        for (int j = 0; j < ns; j++) {
		            float val = allData[i * ns + j] * gain;
		            
		            // Genliği normalize et (0.0 ile 1.0 arasına çek)
		            float norm = (val + safeMax) / (2.0f * safeMax);
		            if (norm < 0.0f) norm = 0.0f;
		            if (norm > 1.0f) norm = 1.0f;

		            // Paletten Rengi Seç
		            int colorIndex = (int) (norm * 20.0f);
		            float r = glPalette[colorIndex][0];
		            float g = glPalette[colorIndex][1];
		            float b = glPalette[colorIndex][2];

		            float yTop = j * cellHeight;
		            float yBottom = yTop + cellHeight;

		            // Yeniden yaratmak yerine mevcut global dizilerin üzerine yazıyoruz
		            // --- 1. SOL ÜST KÖŞE ---
		            globalVertices[vIdx++] = xLeft; globalVertices[vIdx++] = yTop;
		            globalColors[cIdx++] = r; globalColors[cIdx++] = g; globalColors[cIdx++] = b;

		            // --- 2. SAĞ ÜST KÖŞE ---
		            globalVertices[vIdx++] = xRight; globalVertices[vIdx++] = yTop;
		            globalColors[cIdx++] = r; globalColors[cIdx++] = g; globalColors[cIdx++] = b;

		            // --- 3. SAĞ ALT KÖŞE ---
		            globalVertices[vIdx++] = xRight; globalVertices[vIdx++] = yBottom;
		            globalColors[cIdx++] = r; globalColors[cIdx++] = g; globalColors[cIdx++] = b;

		            // --- 4. SOL ALT KÖŞE ---
		            globalVertices[vIdx++] = xLeft; globalVertices[vIdx++] = yBottom;
		            globalColors[cIdx++] = r; globalColors[cIdx++] = g; globalColors[cIdx++] = b;
		        }
		    }

		    // 2. GPU'ya SADECE Dolu Olan Kısımları Yükle (Yeni metoda 'requiredLength' gönderiyoruz)
		    int vertexVboId = uploadToGPU(globalVertices, requiredVerticesLength, GL_ARRAY_BUFFER);
		    int colorVboId = uploadToGPU(globalColors, requiredColorsLength, GL_ARRAY_BUFFER);

		    // Geriye Vertex VBO'sunu, Renk VBO'sunu ve Toplam Çizilecek Köşe Sayısını dön
		    return new int[]{vertexVboId, colorVboId, totalVertices};
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

		// Integer veriler (IBO/Index) için (Aşırı yükleme - Overloading)
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

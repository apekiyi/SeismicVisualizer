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
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

public class Density {
	
	public void density1(int ntr, float[] data, int ns, int visible) {
	    var drawList = ImGui.getBackgroundDrawList();
	    
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();

	    int visibleTraces = Math.min(ntr, visible); 
	    
	    // Daha önce belirlediğimiz padding (margin) mantığını koruyoruz
	    float margin = sw / (float)(visibleTraces + 1); 
	    float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, visibleTraces - 1);
	    float dynamicSampleSpacing = sh / (float)ns;

	    // Hücre genişliği (izler arası boşluğun yarısı kadar veya tam iz genişliği)
	    float cellWidth = dynamicTraceSpacing;
	    float cellHeight = dynamicSampleSpacing;

	    for (int i = 0; i < visibleTraces; i++) {
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        
	        for (int j = 0; j < ns; j++) {
	            int idx = i * ns + j;
	            
	            // Genliği 0-1 arasına normalize et (Sismik verinin -1 ile 1 arası olduğunu varsayarsak)
	            // Mutlak değer (Absolute) sismik karakteri daha iyi gösterir
	            float amp = Math.abs(data[idx]); 
	            amp = Math.min(1.0f, Math.max(0.0f, amp)); // Clamp 0-1
	            
	            // Renk hesabı: Min (0) -> Beyaz (255), Max (1) -> Siyah (0)
	            int grayValue = (int) ((1.0f - amp) * 255);
	            int color = ImGui.getColorU32(grayValue/255f, grayValue/255f, grayValue/255f, 1.0f);
	            
	            // Hücrenin köşeleri
	            float x1 = xAnchor - (cellWidth / 2.0f);
	            float y1 = j * cellHeight;
	            float x2 = xAnchor + (cellWidth / 2.0f);
	            float y2 = (j + 1) * cellHeight;
	            
	            // Dikdörtgeni çiz
	            drawList.addRectFilled(x1, y1, x2, y2, color);
	        }
	    }
	}
	
	public void density2(int ntr, float[] data, int ns, int visible, float gainValue) {
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

	    int visibleTraces = Math.min(ntr, visible); 
	    float margin = sw / (float)(visibleTraces + 1); 
	    float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, visibleTraces - 1);
	    float dynamicSampleSpacing = sh / (float)ns;

	    float halfWidth = dynamicTraceSpacing / 2.0f;

	    for (int i = 0; i < visibleTraces; i++) {
	        float xAnchor = margin + (i * dynamicTraceSpacing);
	        float xLeft = xAnchor - halfWidth;
	        float xRight = xAnchor + halfWidth;

	        glBegin(GL_QUAD_STRIP);
	        for (int j = 0; j <= ns; j++) {
	            int sampleIdx = Math.min(j, ns - 1);
	            int idx = i * ns + sampleIdx;
	            
	            // 1. Mutlak değer (abs) kullanmıyoruz, genliği direkt alıyoruz
	            float amp = data[idx] * gainValue;
	            
	            // 2. Normalizasyon (-1.0 ile 1.0 arasına sıkıştır)
	            float norm = Math.min(1.0f, Math.max(-1.0f, amp));
	            
	            // 3. Renk Dönüşümü: 
	            // -1 -> 0.0 (Siyah)
	            //  0 -> 0.5 (Gri)
	            //  1 -> 1.0 (Beyaz)
	            float gray = 0.5f + (norm * 0.5f);
	            
	            glColor3f(gray, gray, gray); 

	            float y = j * dynamicSampleSpacing;
	            glVertex2f(xLeft, y);
	            glVertex2f(xRight, y);
	        }
	        glEnd();
	    }

	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	    glPopAttrib();
	}
	
	public void density3(int shader, int textureId, int vao, float gainValue) {
	    if (shader <= 0 || textureId <= 0) return;

	    glUseProgram(shader);

	    // 1. GÜNCEL BOYUTLARI AL VE VIEWPORT'U GÜNCELLE
	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();
	    
	    // Bu satır çizimin yeni pencere boyutuna yayılmasını sağlar
	    glViewport(0, 0, (int)sw, (int)sh); 

	    // 2. Texture Bağlantıları
	    glActiveTexture(GL_TEXTURE0); 
	    glBindTexture(GL_TEXTURE_2D, textureId);
	    
	    int texLoc = glGetUniformLocation(shader, "uSeismicTex");
	    if (texLoc != -1) {
	        glUniform1i(texLoc, 0); 
	    }

	    // 3. Uniform Değerleri
	    glUniform1f(glGetUniformLocation(shader, "uGain"), gainValue);
	    glUniform1f(glGetUniformLocation(shader, "uGamma"), 1.0f);

	    // 4. PROJEKSİYON MATRİSİ (sw ve sh değiştikçe matris de burada güncellenmiş olur)
	    float[] ortho = {
	        2.0f/sw, 0, 0, 0,
	        0, -2.0f/sh, 0, 0,
	        0, 0, -1, 0,
	        -1, 1, 0, 1
	    };
	    int projLoc = glGetUniformLocation(shader, "uProjection");
	    if (projLoc != -1) {
	        glUniformMatrix4fv(projLoc, false, ortho);
	    }

	    // 5. Çizim (VAO zaten 0-1 arası olduğu için projeksiyon matrisi onu sw, sh boyutuna açar)
	    glBindVertexArray(vao);
	    glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
	    glBindVertexArray(0);
	    
	    glUseProgram(0);
	}
	
	public void density4(int vboId, int totalVertices) {
	    if (vboId <= 0) return;

	    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	    glClear(GL_COLOR_BUFFER_BIT);

	    float sw = ImGui.getIO().getDisplaySizeX();
	    float sh = ImGui.getIO().getDisplaySizeY();

	    glPushAttrib(GL_ALL_ATTRIB_BITS);
	    glViewport(0, 0, (int)sw, (int)sh);

	    glMatrixMode(GL_PROJECTION);
	    glPushMatrix();
	    glLoadIdentity();
	    glOrtho(0, sw, sh, 0, -1, 1); // Sarı kareyi getiren o kutsal ayar
	    
	    glMatrixMode(GL_MODELVIEW);
	    glPushMatrix();
	    glLoadIdentity();

	    glBindBuffer(GL_ARRAY_BUFFER, vboId);
	    
	    // Her nokta 5 float (20 byte). X,Y için ilk 2 float. R,G,B için sonraki 3 float.
	    glEnableClientState(GL_VERTEX_ARRAY);
	    glEnableClientState(GL_COLOR_ARRAY);

	    glVertexPointer(2, GL_FLOAT, 20, 0); 
	    glColorPointer(3, GL_FLOAT, 20, 8); // 2 float atla (8 byte), renkleri oku

	    // Çizim
	    glDrawArrays(GL_QUADS, 0, totalVertices);

	    glDisableClientState(GL_COLOR_ARRAY); 
	    glDisableClientState(GL_VERTEX_ARRAY);
	    glBindBuffer(GL_ARRAY_BUFFER, 0);
	    
	    glPopMatrix();
	    glMatrixMode(GL_PROJECTION);
	    glPopMatrix();
	    glPopAttrib();
	}

	
	public int createShaderProgram() {
		
		String[] vertexSource=DENSITY_VERTEX_SHADER ;
		String[] fragmentSource=DENSITY_FRAGMENT_SHADER ;
		
	    // 1. Vertex Shader'ı Derle
	    int vShader = glCreateShader(GL_VERTEX_SHADER);
	    glShaderSource(vShader, String.join("", vertexSource));
	    glCompileShader(vShader);
	    

	    // 2. Fragment Shader'ı Derle
	    int fShader = glCreateShader(GL_FRAGMENT_SHADER);
	    glShaderSource(fShader, String.join("", fragmentSource));
	    glCompileShader(fShader);

	    // 3. Shader Programını Oluştur ve Bağla (Link)
	    int program = glCreateProgram();
	    glAttachShader(program, vShader);
	    glAttachShader(program, fShader);
	    glLinkProgram(program);
	    
	    // 4. Geçici shader nesnelerini temizle
	    glDeleteShader(vShader);
	    glDeleteShader(fShader);

	    return program;
	}
	
	
		

			
			public int uploadSeismicToGPU(float[] data, int ns, int ntr) {
			    int texId = glGenTextures();
			    glBindTexture(GL_TEXTURE_2D, texId);
			    glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // Çok önemli!

			    FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
			    buffer.put(data);
			    buffer.flip();

			    // Sismik veri için en keskin ayarlar
			    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			    // Verin genelde [ntr][ns] formatında olduğu için:
			    glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, ns, ntr, 0, GL_RED, GL_FLOAT, buffer);

			    glBindTexture(GL_TEXTURE_2D, 0);
			    return texId;
			}
		
		

		public int setupVAO() {
		    float[] vertices = {
		        // X, Y (Pos)   // U, V (Tex)
		        0.0f, 0.0f,     0.0f, 0.0f,
		        1.0f, 0.0f,     1.0f, 0.0f,
		        1.0f, 1.0f,     1.0f, 1.0f,
		        0.0f, 1.0f,     0.0f, 1.0f
		    };
		    int vao = glGenVertexArrays();
		    int vbo = glGenBuffers();
		    glBindVertexArray(vao);
		    glBindBuffer(GL_ARRAY_BUFFER, vbo);
		    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
		    glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
		    glEnableVertexAttribArray(0);
		    glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
		    glEnableVertexAttribArray(1);
		    return vao;
		}
		
		public final String[] DENSITY_VERTEX_SHADER = new String[] {
			    "#version 330 core\n",
			    "layout(location = 0) in vec2 aPos;\n",
			    "layout(location = 1) in vec2 aTexCoord;\n",
			    "\n",
			    "out vec2 vTexCoord;\n",
			    "\n",
			    "void main()\n",
			    "{\n",
			    "    // 180 DERECE VE EKSEN DÜZELTME:\n",
			    "    // Eğer görüntü yan ve ters ise, x ve y'yi takas edip \n",
			    "    // bir ekseni 1.0'dan çıkararak (mirroring) düzeltiriz.\n",
			    "    vTexCoord =vec2(1.0 - aTexCoord.y, aTexCoord.x);\n",
			    "    \n",
			    "    gl_Position = vec4(aPos * 2.0 - 1.0, 0.0, 1.0);\n",
			    "}\n"
			};
		
		public final String[] DENSITY_FRAGMENT_SHADER = new String[] {
			    "#version 330 core\n",
			    "\n",
			    "in vec2 vTexCoord;\n",
			    "out vec4 FragColor;\n",
			    "\n",
			    "uniform sampler2D uSeismicTex;\n",
			    "uniform float uGain;\n",
			    "uniform float uGamma;\n",
			    "\n",
			    "void main()\n",
			    "{\n",
			    "    // 1. Ham sismik veriyi oku (-1.0 ile 1.0 arası bekliyoruz)\n",
			    "    float amp = texture(uSeismicTex, vTexCoord).r;\n",
			    "\n",
			    "    // 2. Kazanç (Gain) uygula\n",
			    "    float val = amp * uGain;\n",
			    "\n",
			    "    // 3. Normalizasyon: -1 (Siyah), 0 (Gri), 1 (Beyaz) aralığına çek\n",
			    "    // Sismik genliği -1 ile 1 arasına sıkıştırıyoruz\n",
			    "    float norm = clamp(val, -1.0, 1.0);\n",
			    "\n",
			    "    // 4. Renk Hesaplama:\n",
			    "    // norm = -1 ise (0.5 + -0.5) = 0.0 (Siyah)\n",
			    "    // norm =  0 ise (0.5 +  0.0) = 0.5 (Gri)\n",
			    "    // norm =  1 ise (0.5 +  0.5) = 1.0 (Beyaz)\n",
			    "    float gray = 0.5 + (norm * 0.5);\n",
			    "\n",
			    "    // 5. İstersen Gamma ile kontrastı ayarla\n",
			    "    gray = pow(gray, uGamma);\n",
			    "\n",
			    "    FragColor = vec4(vec3(gray), 1.0);\n",
			    "}\n"
			};
		


		public int[] createVBO(float[] allData, int ntr, int ns, float sw, float sh, float gain) {
		    // Her örnek için 4 nokta (QUAD), her nokta 5 float (X,Y,R,G,B)
		    int verticesPerSample = 4;
		    int totalFloats = ntr * ns * verticesPerSample * 5;
		    FloatBuffer buffer = BufferUtils.createFloatBuffer(totalFloats);
		    
		    float margin = sw / (float)(ntr + 1); 
		    float dynamicTraceSpacing = (sw - (2 * margin)) / Math.max(1, ntr - 1);
		    float ySpacing = sh / (float)(ns - 1);
		    float halfWidth = dynamicTraceSpacing * 0.5f;

		    float localMax = 0.000001f;
		    for (int k = 0; k < Math.min(allData.length, 20000); k++) {
		        if (Math.abs(allData[k]) > localMax) localMax = Math.abs(allData[k]);
		    }

		    for (int i = 0; i < ntr; i++) {
		        float xLeft = (margin + (i * dynamicTraceSpacing)) - halfWidth;
		        float xRight = (margin + (i * dynamicTraceSpacing)) + halfWidth;

		        for (int j = 0; j < ns; j++) {
		            float amplitude = allData[i * ns + j];
		            float norm = (amplitude / localMax) * gain;
		            float clamped = Math.max(-1.0f, Math.min(1.0f, norm));
		            float gray = 0.5f + (clamped * 0.5f);
		            
		            float yTop = j * ySpacing;
		            float yBottom = (j + 1) * ySpacing;
		            
		            if(j==ns-1)
		            {
		            	System.out.println(xLeft+" "+xRight+" "+yTop+" "+yBottom+" "+gray);
		            }

		            // 1. Sol Üst | 2. Sağ Üst | 3. Sağ Alt | 4. Sol Alt
		            buffer.put(xLeft).put(yTop).put(gray).put(gray).put(gray);
		            buffer.put(xRight).put(yTop).put(gray).put(gray).put(gray);
		            buffer.put(xRight).put(yBottom).put(gray).put(gray).put(gray);
		            buffer.put(xLeft).put(yBottom).put(gray).put(gray).put(gray);
		        }
		    }
		    buffer.flip(); // GPU okumadan önce "başa sar" komutu

		    // Manuel Upload (uploadToGPU metodundaki gizemli hatayı aşmak için)
		    int vboId = glGenBuffers();
		    glBindBuffer(GL_ARRAY_BUFFER, vboId);
		    glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
		    glBindBuffer(GL_ARRAY_BUFFER, 0);

		    return new int[]{vboId, ntr * ns * 4};
		}
		
		 
		// Float veriler (VBO) için
		 public int uploadToGPU(float[] data, int target) {
		     int bufferId = glGenBuffers();
		     glBindBuffer(target, bufferId);
		     
		     FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
		     buffer.put(data).flip();
		     
		     glBufferData(target, buffer, GL_STATIC_DRAW);
		     glBindBuffer(target, 0); // İşlem bitince bağlantıyı kes
		     return bufferId;
		 }

		 // Integer veriler (IBO/Index) için (Aşırı yükleme - Overloading)
		 public int uploadToGPU(int[] data, int target) {
		     int bufferId = glGenBuffers();
		     glBindBuffer(target, bufferId);
		     
		     IntBuffer buffer = BufferUtils.createIntBuffer(data.length);
		     buffer.put(data).flip();
		     
		     glBufferData(target, buffer, GL_STATIC_DRAW);
		     glBindBuffer(target, 0);
		     return bufferId;
		 }
		

}

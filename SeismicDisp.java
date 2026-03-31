import imgui.ImGui;
import imgui.ImVec2;
import imgui.app.Application;
import imgui.app.Configuration;
import static org.lwjgl.opengl.GL15.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;




public class SeismicDisp extends Application {

    private segyread reader;
    public Wiggle w=new Wiggle();
    public ColorDensity cd=new ColorDensity();
    public Density d=new Density();
    private float[] gain = {10.0f};
    
    int ns,not;
    float[] data;
    private ImVec2[] tracePoints;
    int textureId,colorTextureID;
    int shader,shader_va,shader_color;
    float max;
    
    
   
    
    //Wiggle4
    public int vboId = -1;
    public int iboId = -1;
    public int totalIndices = 0;
    public int densityVAO=-1;
    
    //Variable Area
    public VariableArea va;

    @Override
    protected void configure(Configuration config) {
        config.setTitle("Sismik Performans Testi");
    }
    
    int[] cdensity;
    float[][] colormap;
    
    
    String file="test.sgy";
    int yontem=4;
    int W=1;
    int VA=1;
    int VD=0;
    int now=10;					//number of wiggles
    
 // --- YENİ EKLENEN KURUCU METOD (CONSTRUCTOR) ---
    public SeismicDisp(String file, int now, int yontem, int W, int VA, int VD) {
        this.file = file;
        this.now = now;
        this.yontem = yontem;
        this.W = W;
        this.VA = VA;
        this.VD = VD;
    }
    

    @Override
    protected void initImGui(Configuration config) {
        super.initImGui(config);
        // Sismik veriyi burada yükleyelimf
        
        
       reader = new segyread();
       reader.read(file); 
       ns=(reader.ns);
       data=reader.data;
       not=reader.numberOfTraces;
       max=reader.max;
       
       w=new Wiggle();
       va=new VariableArea();
       
       if(yontem==1 && W==1)
       {
    	   this.tracePoints = new imgui.ImVec2[ns];
           for (int i = 0; i < ns; i++) {
               this.tracePoints[i] = new imgui.ImVec2();
           }
       }
       
       if(yontem==3)
       {
    	 textureId=d.uploadSeismicToGPU(data, ns, not);
       }
       if(yontem==3 && W==1)
       {
    	  shader=w.initLineShaders();
       }
       if(yontem==3 && VA==1)
       {
         shader_va=va.initVaShaders();
       }
       
       if(yontem==1 && VD==1)
       {
    	   cdensity=cd.densitypalette();
       }
       if(yontem==2 && VD==1)
       {
    	   colormap=cd.initGLPalette();
       }
       if(yontem==3 && VD==1)
       {
    	   shader_color=cd.initDensityShaders();
           colorTextureID=cd.initColormapTexture();
       }
       if(yontem==4 && VD==1)
       {
    	   colormap=cd.initGLPalette();
       }
       
       
    
       //1.3 Wiggle
 //     
 //      textureId=d.uploadSeismicToGPU(data, ns, not);
       
       
       
       /*
       //Wiggle 1 için ön hazırlık
      
       
       
      
       
         //Wiggle3 için ön hazırlık
       shader=w.initLineShaders();
       textureId=d.uploadSeismicToGPU(data, ns, not);
      
       //Variable Area için Hazırlık
       shader_va=va.initVaShaders();
       textureId=d.uploadSeismicToGPU(data, ns, not);
       */
       /*
       //Color Density
       //4.1 dearimgui
      
       //4.2 opengl
       colormap=cd.initGLPalette();
       //4.3 texture
       textureId=d.uploadSeismicToGPU(data, ns, not);
       shader_color=cd.initDensityShaders();
       colorTextureID=cd.initColormapTexture();
       */
    }
    
    private float lastSw = -1.0f;
    private float lastSh = -1.0f;
    private float lastGain = -1.0f;
    float sw=-1;
    float sh=-1;
    
   
    @Override
    protected void dispose() {
        // 1. Senin arka plan RAM okuyucunu güvenlice durdur
        if (memoryMonitor != null) {
            memoryMonitor.stop();
            System.out.println("Memory Monitor durduruldu.");
        }
        
        // 2. ImGui'nin kendi standart bellek temizliğini yapmasına izin ver
        super.dispose();
    }
  
    
    private MemoryMonitor memoryMonitor = new MemoryMonitor();
 // Performans ölçümü için gereken kalıcı değişkenler
    private float[] frameTimes = new float[120];
    private int frameTimeIdx = 0;
  
    public void process() {
        
        long memory = memoryMonitor.getMemoryMB();
        
        // --- FRAME TIME HESAPLAMA VE KAYDETME ---
        // DeltaTime saniye cinsindendir, 1000 ile çarpıp milisaniyeye (ms) çeviriyoruz
        float currentFrameTimeMs = ImGui.getIO().getDeltaTime() * 1000.0f;
        
        // Değeri diziye ekle ve indeksi bir sonrakine kaydır (120'ye ulaşınca başa döner)
        frameTimes[frameTimeIdx] = currentFrameTimeMs;
        frameTimeIdx = (frameTimeIdx + 1) % frameTimes.length;

        // Dizi içindeki en yüksek gecikmeyi (Spike/Donma) bul
        float maxFrameTime = 0.0f;
        for (float ft : frameTimes) {
            if (ft > maxFrameTime) {
                maxFrameTime = ft;
            }
        }
        // ----------------------------------------
        
        // 1. Tüm ekranı beyaz ile boya (Kağıt efekti)
        sw = ImGui.getIO().getDisplaySizeX();
        sh = ImGui.getIO().getDisplaySizeY();
        ImGui.getBackgroundDrawList().addRectFilled(0, 0, sw, sh, ImGui.getColorU32(0.1f, 0.1f, 0.2f, 0.0f));
        
        // 2. Kontrol Paneli (Ayarlar)
        ImGui.begin("Ayarlar");
        ImGui.sliderFloat("Kazanç", gain, 0.1f, 500.0f);
        ImGui.text("Seismic File: " + file);
        ImGui.text("Number of Traces: " + now);
        ImGui.text("FPS: " + ImGui.getIO().getFramerate());
        ImGui.text("Memory: " + memory + " MB");
        
        // --- PERFORMANS KANITLAMA ARAYÜZÜ ---
        ImGui.separator();
        ImGui.text("Current Frame Time: " + String.format("%.2f", currentFrameTimeMs) + " ms");
        
        // Donmayı gösteren kırmızı yazı (RGBA: 1,0,0,1)
        ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "Max Spike: " + String.format("%.2f", maxFrameTime) + " ms");
        
        // Frame Time Grafiği (Yüksekliği 80 piksel, Max Y ekseni 100ms)
        // Eğer donman 100ms'den daha uzun sürüyorsa grafikteki 100.0f değerini 300.0f falan yapabilirsin.
        ImGui.plotLines("Frame Time Graph", frameTimes, frameTimes.length, frameTimeIdx, "", 0.0f, 1500.0f, 0, 80);
        ImGui.separator();
        // ------------------------------------
        
        ImGui.end();
        
        // Variable Density
        if(yontem==1 && VD==1) VD1();
        if(yontem==2 && VD==1) VD2();
        if(yontem==3 && VD==1) VD3();
        if(yontem==4 && VD==1) VD4();
        
        // Wiggle
        if(yontem==1 && W==1) W1();
        if(yontem==2 && W==1) W2();
        if(yontem==3 && W==1) W3();
        if(yontem==4 && W==1) W4();
        
        // Variable Area
        if(yontem==1 && VA==1) VA1();
        if(yontem==2 && VA==1) VA2();
        if(yontem==3 && VA==1) VA3();
        if(yontem==4 && VA==1) VA4();
    }
    
// --- 1. DEĞİŞKENLERİ AYIRIYORUZ ---
    
    // Wiggle 4 için Özel Değişkenler
    public int vboId_W = -1;
    public int iboId_W = -1;
    public int totalIndices_W = 0;
    private float lastSw_W = -1.0f;
    private float lastSh_W = -1.0f;
    private float lastGain_W = -1.0f;

    // Variable Area 4 için Özel Değişkenler
    public int vboId_VA = -1;
    public int iboId_VA = -1;
    public int totalIndices_VA = 0;
    private float lastSw_VA = -1.0f;
    private float lastSh_VA = -1.0f;
    private float lastGain_VA = -1.0f;

    // Variable Density 4 için Özel Değişkenler
    int densityVertexVboId = -1;
    int densityColorVboId = -1;
    int densityTotalVertices = 0;
    private float lastSw_VD = -1.0f;
    private float lastSh_VD = -1.0f;
    private float lastGain_VD = -1.0f;

    // --- 2. FONKSİYONLARI GÜNCELLİYORUZ ---

    public void W4() {
        if (gain[0] != lastGain_W || sw != lastSw_W || sh != lastSh_W) {
            if (vboId_W != -1) glDeleteBuffers(vboId_W);
            if (iboId_W != -1) glDeleteBuffers(iboId_W);

            int[] results = w.createVBO(data, now, ns, sw, sh, gain[0]);
            this.vboId_W = results[0];
            this.iboId_W = results[1];
            this.totalIndices_W = results[2];
            
            lastGain_W = gain[0];
            lastSw_W = sw;
            lastSh_W = sh;
        }

        if (vboId_W != -1) {
           w.wiggle4(vboId_W, iboId_W, totalIndices_W, sw, sh);
        }
    }

    public void VA4() {
        if (gain[0] != lastGain_VA || sw != lastSw_VA || sh != lastSh_VA) {
            if (vboId_VA != -1) glDeleteBuffers(vboId_VA);
            if (iboId_VA != -1) glDeleteBuffers(iboId_VA);

            int[] results = va.createVaVBO(data, now, ns, sw, sh, gain[0]);
            this.vboId_VA = results[0];
            this.iboId_VA = results[1];
            this.totalIndices_VA = results[2];
            
            lastGain_VA = gain[0];
            lastSw_VA = sw;
            lastSh_VA = sh;
        }

        if (vboId_VA != -1) {
           va.va4(vboId_VA, iboId_VA, totalIndices_VA, sw, sh);
        }
    }

    public void VD4() {
        if (gain[0] != lastGain_VD || sw != lastSw_VD || sh != lastSh_VD) {
            if (densityVertexVboId != -1) glDeleteBuffers(densityVertexVboId);
            if (densityColorVboId != -1) glDeleteBuffers(densityColorVboId);

            int[] densityResults = cd.createDensityVBO(data, now, ns, sw, sh, gain[0], max, colormap);
            densityVertexVboId = densityResults[0];
            densityColorVboId = densityResults[1];
            densityTotalVertices = densityResults[2];
            
            lastGain_VD = gain[0];
            lastSw_VD = sw;
            lastSh_VD = sh;
        }

        if (densityVertexVboId != -1) {
            cd.density4(densityVertexVboId, densityColorVboId, densityTotalVertices, sw, sh);
        }
    }
    
    public void W1()
    {
    	  w.wiggle1(not, data, gain, ns, tracePoints,now);
    }
    
    public void W2()
    {
    	w.wiggle2(not, now, gain, ns, data);
    }
    public void W3()
    {
    	  w.wiggle3(shader, textureId, max, ns, gain,not,now);
    }
   
    
    public void VA1()
    {
    	va.va1(not, data, gain, ns, now);
    }
    
    public void VA2()
    {
    	va.va2(not, now, gain, ns, data);
    }
    
    public void VA3()
    {
    	 va.va3(shader_va, textureId, max, ns, gain,not,now);
    }
    
    
    
 
	
	public void VD1()
	{
		 cd.density1(not, data, gain, ns, now, max, cdensity);
	}
    
	public void VD2()
	{
		 cd.density2(not, data, gain, ns,now, max, colormap);
	}
	
	public void VD3()
	{
		cd.density3(shader_color, textureId, colorTextureID, max, gain, now, not);
	}
	
   
    
    
    
    int totalVertices=-1;
    
    public void D4() {
    	
        // Sadece Gain veya Pencere boyutu değiştiğinde VBO'yu tazele
        if (gain[0] != lastGain || sw != lastSw || sh != lastSh) {
            if (vboId != -1) glDeleteBuffers(vboId); // Eskiyi mutlaka SİL!
            
            // ntr olarak "now" (iz sayısı) gönderiyoruz
            
            System.out.println(sw);
            
            int[] results = d.createVBO(data, now,not,sw, sh, gain[0]);
            this.vboId = results[0];
            totalVertices = results[1];

            lastGain = gain[0];
            lastSw = sw;
            lastSh = sh;
        }

        // Çizim her karede çağrılır
        if (vboId != -1) {
            d.density4(vboId, totalVertices);
        }
    }

   

    public static void main(String[] args) {
        // Eğer dışarıdan parametre gelmediyse güvenli çıkış yap veya default değerlerle başlat
        if (args.length < 6) {
            System.out.println("Eksik parametre! Kullanım: java -jar SeismicDisplay.jar <file> <traces> <engine> <w> <va> <vd>");
            return;
        }

        try {
            // Gelen String argümanları ilgili tiplere çeviriyoruz
            String file = args[0];
            int traces = Integer.parseInt(args[1]);
            int engine = Integer.parseInt(args[2]);
            int w = Integer.parseInt(args[3]);
            int va = Integer.parseInt(args[4]);
            int vd = Integer.parseInt(args[5]);

            // Uygulamayı bu parametrelerle başlatıyoruz
            imgui.app.Application.launch(new SeismicDisp(file, traces, engine, w, va, vd));
            
        } catch (NumberFormatException e) {
            System.err.println("Parametre formatı hatalı: " + e.getMessage());
        }
    }
}
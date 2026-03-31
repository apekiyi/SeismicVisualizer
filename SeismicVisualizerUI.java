import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class SeismicVisualizerUI extends JFrame {

    private JTextField selectedFileField;
    private JTextField numTracesField;
    
    // Rendering Engines (Radyo Butonları - Tekli Seçim)
    private JRadioButton cpuButton;
    private JRadioButton openglButton;
    private JRadioButton shaderButton;
    private JRadioButton vboButton;

    // Display Modes (Onay Kutuları - Çoklu Seçim)
    private JCheckBox wiggleCheckBox;
    private JCheckBox vaCheckBox;
    private JCheckBox vdCheckBox;

    public SeismicVisualizerUI() {
        // Ana Frame Ayarları
        setTitle("Seismic Data Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false); // Pencere boyutunu sabitle, daha şık durur
        setLayout(new BorderLayout(10, 10));

        // Ana Paneli Oluştur
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20)); // Kenarlardan ferah boşluklar

        // --- 1 & 2. DATA SOURCE BÖLÜMÜ (Dosya Seçimi ve Trace Sayısı) ---
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
        dataPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dataPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Data Source"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10) // Çerçeve içi boşluk
        ));

        // Dosya Seçim Paneli
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton openFileButton = new JButton("Select File");
        openFileButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        selectedFileField = new JTextField(25);
        selectedFileField.setEditable(false);
        
        openFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                int result = fileChooser.showOpenDialog(SeismicVisualizerUI.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    selectedFileField.setText(selectedFile.getAbsolutePath());
                }
            }
        });
        
        filePanel.add(openFileButton);
        filePanel.add(Box.createRigidArea(new Dimension(10, 0)));
        filePanel.add(selectedFileField);

        // Trace Sayısı Paneli
        JPanel tracePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tracePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel traceLabel = new JLabel("Number of Traces: ");
        numTracesField = new JTextField(10);
        
        tracePanel.add(traceLabel);
        tracePanel.add(Box.createRigidArea(new Dimension(5, 0))); // Etiket ile kutu arası hafif boşluk
        tracePanel.add(numTracesField);

        // Alt panelleri Data Source paneline ekle
        dataPanel.add(filePanel);
        dataPanel.add(Box.createRigidArea(new Dimension(0, 15))); // İki satır arası boşluk
        dataPanel.add(tracePanel);

        mainPanel.add(dataPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15))); // Paneller arası boşluk


        // --- 3. RENDERING ENGINE BÖLÜMÜ ---
        JPanel enginePanel = new JPanel();
        enginePanel.setLayout(new BoxLayout(enginePanel, BoxLayout.Y_AXIS));
        enginePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        enginePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Rendering Engine (Select One)"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        cpuButton = new JRadioButton("1. CPU-Based Immediate Mode");
        openglButton = new JRadioButton("2. Legacy OpenGL Direct Draw");
        shaderButton = new JRadioButton("3. Shader & Texture Pipeline");
        vboButton = new JRadioButton("4. VBO (Vertex Buffer Object)");

        // Sadece birinin seçilmesini sağlayan grup
        ButtonGroup engineGroup = new ButtonGroup();
        engineGroup.add(cpuButton);
        engineGroup.add(openglButton);
        engineGroup.add(shaderButton);
        engineGroup.add(vboButton);
        cpuButton.setSelected(true); // Varsayılan seçim

        enginePanel.add(cpuButton);
        enginePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        enginePanel.add(openglButton);
        enginePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        enginePanel.add(shaderButton);
        enginePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        enginePanel.add(vboButton);
        
        mainPanel.add(enginePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));


        // --- 4. DISPLAY MODE BÖLÜMÜ ---
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new BoxLayout(modePanel, BoxLayout.Y_AXIS));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Display Mode"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        wiggleCheckBox = new JCheckBox("Wiggle Trace");
        vaCheckBox = new JCheckBox("Variable Area (VA)");
        vdCheckBox = new JCheckBox("Variable Density (VD)");

        modePanel.add(wiggleCheckBox);
        modePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        modePanel.add(vaCheckBox);
        modePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        modePanel.add(vdCheckBox);
        
        mainPanel.add(modePanel);

        // Ana paneli pencereye ekle
        add(mainPanel, BorderLayout.CENTER);

        // --- 5. RENDER BUTONU (Alt Kısım) ---
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0)); // Alt kısımdan biraz boşluk
        JButton renderButton = new JButton("Render Visualization");
        renderButton.setFont(new Font("Arial", Font.BOLD, 14));
        renderButton.setPreferredSize(new Dimension(200, 40));
        renderButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        renderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startRendering();
            }
        });
        
        bottomPanel.add(renderButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // İçeriğe göre pencere boyutunu otomatik ayarla ve ekrana ortala
        pack();
        setLocationRelativeTo(null);
    }

   int engine=0;
    
    private void startRendering() {
        // 1. Dosya Yolunu Al ve Kontrol Et
        String filePath = selectedFileField.getText();
        if (filePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a valid SEGY file!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. İz (Trace) Sayısını Al ve Kontrol Et
        int traces;
        try {
            traces = Integer.parseInt(numTracesField.getText());
            if (traces <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid number of traces!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 3. Hangi Motorun Seçildiğini Bul
        engine = 1; // Default: CPU
        if (openglButton.isSelected()) engine = 2;
        else if (shaderButton.isSelected()) engine = 3;
        else if (vboButton.isSelected()) engine = 4;

        // 4. Çizim Modlarını Bul
        int w = wiggleCheckBox.isSelected() ? 1 : 0;
        int va = vaCheckBox.isSelected() ? 1 : 0;
        int vd = vdCheckBox.isSelected() ? 1 : 0;

        if (w == 0 && va == 0 && vd == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one display mode (Wiggle, VA, VD)!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

     // 5. Harici JAR dosyasını ProcessBuilder ile başlat
        try {
            // Not: "SeismicDisplay.jar" dosyasının, arayüz programıyla aynı klasörde olduğunu varsayıyoruz.
            ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", "SeismicDisplay.jar", // Çalıştırılacak komut
                filePath,                             // args[0]
                String.valueOf(traces),               // args[1]
                String.valueOf(engine),               // args[2]
                String.valueOf(w),                    // args[3]
                String.valueOf(va),                   // args[4]
                String.valueOf(vd)                    // args[5]
            );
            
            // İkinci programın konsol çıktılarını (varsa hataları) ana programın konsoluna yönlendirir.
            // Hata ayıklama için çok faydalıdır.
            pb.inheritIO(); 
            
            // Programı başlat (UI'ı dondurmaz, bağımsız çalışır)
            Process process = pb.start();
            
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "JAR dosyası başlatılamadı: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        // Arayüzün işletim sisteminin native görünümünde açılmasını sağlar
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SeismicVisualizerUI().setVisible(true);
            }
        });
    }
}
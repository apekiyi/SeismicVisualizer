Seismic Data Visualizer & Benchmarking Tool

This open-source tool is designed for high-performance interactive visualization of large-scale SEG-Y seismic datasets. It allows researchers to compare four different graphics architectures: CPU-Based, Legacy OpenGL, VBO, and Shader & Texture Pipelines.

Key Features:


Performance Metrics: Real-time tracking of FPS, Latency (Spikes), and RAM consumption.


Display Formats: Supports Wiggle Trace, Variable Area (VA), and Variable Density (VD) modes.


Hardware Efficiency: Specifically optimized to demonstrate the stability of Shader & Texture architectures on massive datasets.

How to Run:

Ensure Java 11+ is installed.

Place SeismicDisplay.jar in the same folder as the main application.

Double-click SeismicVisualizer.jar or run via terminal: java -jar SeismicVisualizer.jar.

Technical Insights:
The project highlights how modern GPU-accelerated pipelines alleviate computational burdens compared to traditional CPU-bound rendering.

License: MIT License.


If used in research, please cite: Pekiyi, A. (2026).

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial por defecto: 100 ms (10 Hz)
    private long contadorTicks = 0;

    // --- Componentes Gráficos JFreeChart (Subtarea #14) ---
    private static final int MAX_MUESTRAS_VISIBLES = 100; // Ventana deslizante (Ring Buffer)
    private int canalSeleccionado = 0; // Canal activo por defecto (CH1 = índice 0)
    private XYSeries serieCanalActivo;
    private XYSeriesCollection datasetGrafica;
    private JFreeChart graficoLineas;
    private ChartPanel panelGrafico;

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        inicializarGraficaJFreeChart();
        configurarTimer();
        conectarEventosIniciales();
    }

    /**
     * Inicializa el lienzo, la serie temporal y la ventana deslizante (Subtarea
     * #14).
     */
    private void inicializarGraficaJFreeChart() {
        // 1. Serie de datos con límite estricto de muestras para proteger la RAM
        this.serieCanalActivo = new XYSeries("CH1");
        this.serieCanalActivo.setMaximumItemCount(MAX_MUESTRAS_VISIBLES);

        this.datasetGrafica = new XYSeriesCollection(this.serieCanalActivo);

        // 2. Creación del gráfico de líneas en tiempo real
        this.graficoLineas = ChartFactory.createXYLineChart(
                "Canal 1 - " + TipoOndaSeguro(0), // Título inicial
                "Tiempo (s)", // Eje X
                "Voltaje (V)", // Eje Y
                this.datasetGrafica,
                PlotOrientation.VERTICAL,
                true, // Leyenda
                false, // Tooltips
                false // URLs
        );

        // 3. Estilización y fijación de escala vertical analógica (0.0 V a 5.0 V)
        XYPlot plot = this.graficoLineas.getXYPlot();
        plot.setBackgroundPaint(new Color(245, 245, 245));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setRange(0.0, 5.0); // Rango de la guía

        this.panelGrafico = new ChartPanel(this.graficoLineas);
        this.panelGrafico.setMouseWheelEnabled(true);
    }

    private void configurarTimer() {
        timerMuestreo = new Timer(periodoMuestreoMs, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ejecutarCicloMuestreo();
            }
        });
    }

    /**
     * Ciclo periódico: muestrea el modelo y alimenta la serie gráfica (Subtarea
     * #14).
     */
    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();
        double valorMuestraActiva = analogicas[canalSeleccionado];

        // Actualización dinámica de la gráfica en tiempo real
        serieCanalActivo.add(tiempoActual, valorMuestraActiva);

        // Salida formateada de verificación
        System.out.printf("[Tick #%d | t = %.2f s | Ts = %d ms] CH%d: %.2f V | Dig: %s%n",
                contadorTicks,
                tiempoActual,
                periodoMuestreoMs,
                (canalSeleccionado + 1),
                valorMuestraActiva,
                Arrays.toString(digitales));
    }

    /**
     * Conmuta la fuente analógica hacia el arreglo
     * analogicas[canalSeleccionado].
     */
    public void cambiarCanalSeleccionado(int nuevoCanal) {
        if (nuevoCanal < 0 || nuevoCanal >= Data.NUM_ANALOGICAS) {
            return;
        }

        this.canalSeleccionado = nuevoCanal;

        // Limpiar el trazo para evitar traslapar líneas entre señales distintas
        this.serieCanalActivo.clear();
        this.serieCanalActivo.setKey("CH" + (nuevoCanal + 1));

        String nombreCanal = "Canal " + (nuevoCanal + 1) + " - " + TipoOndaSeguro(nuevoCanal);
        this.graficoLineas.setTitle(nombreCanal);

        System.out.println(">> [Controlador] Conmutado a Canal Analógico CH" + (nuevoCanal + 1));
    }

    /**
     * Incrusta el ChartPanel en el contenedor Swing provisto por la Vista.
     */
    public void incrustarGrafica(JPanel contenedor) {
        if (contenedor != null) {
            contenedor.setLayout(new BorderLayout());
            contenedor.removeAll();
            contenedor.add(panelGrafico, BorderLayout.CENTER);
            contenedor.revalidate();
            contenedor.repaint();
        }
    }

    public ChartPanel getPanelGrafico() {
        return panelGrafico;
    }

    public int getCanalSeleccionado() {
        return canalSeleccionado;
    }

    private String TipoOndaSeguro(int canal) {
        try {
            return modelo.getNombresAnalogicas()[canal];
        } catch (Exception e) {
            return "Señal Analógica";
        }
    }

    private void conectarEventosIniciales() {
        // Conexión pasiva preparada para cuando Santiago complete VentanaPrincipal
    }

    public void actualizarPeriodo(int nuevoPeriodoMs) {
        if (nuevoPeriodoMs > 0) {
            this.periodoMuestreoMs = nuevoPeriodoMs;
            if (timerMuestreo != null) {
                timerMuestreo.setDelay(nuevoPeriodoMs);
            }
            if (modelo != null) {
                modelo.setPeriodoMuestreoMs(nuevoPeriodoMs);
            }
            System.out.println(">> [Controlador] Ts reconfigurado a: " + nuevoPeriodoMs + " ms");
        }
    }

    public void iniciarAdquisicion() {
        if (timerMuestreo != null && !timerMuestreo.isRunning()) {
            timerMuestreo.start();
            System.out.println(">> Adquisición en tiempo real iniciada.");
        }
    }

    public void detenerAdquisicion() {
        if (timerMuestreo != null && timerMuestreo.isRunning()) {
            timerMuestreo.stop();
            System.out.println(">> Adquisición detenida.");
        }
    }

    public boolean isEjecutando() {
        return timerMuestreo != null && timerMuestreo.isRunning();
    }
}

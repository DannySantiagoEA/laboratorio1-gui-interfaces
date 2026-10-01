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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    private int canalSeleccionado = 0;
    private XYSeries serieCanalActivo;
    private XYSeriesCollection datasetGrafica;
    private JFreeChart graficoLineas;
    private ChartPanel panelGrafico;

    // --- Búfer de Extracción de Muestras Temporales (Subtarea #21 / HU-04) ---
    // Almacena pares ordenados [ti, Vi] sincronizados con el Ts activo
    private final List<double[]> bufferMuestrasCanalActivo = Collections.synchronizedList(new ArrayList<>());

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        inicializarGraficaJFreeChart();
        configurarTimer();
        conectarEventosIniciales();
    }

    private void inicializarGraficaJFreeChart() {
        this.serieCanalActivo = new XYSeries("CH1");
        this.serieCanalActivo.setMaximumItemCount(MAX_MUESTRAS_VISIBLES);

        this.datasetGrafica = new XYSeriesCollection(this.serieCanalActivo);

        this.graficoLineas = ChartFactory.createXYLineChart(
                "Canal 1 - " + tipoOndaSeguro(0),
                "Tiempo (s)",
                "Voltaje (V)",
                this.datasetGrafica,
                PlotOrientation.VERTICAL,
                true,
                false,
                false
        );

        XYPlot plot = this.graficoLineas.getXYPlot();
        plot.setBackgroundPaint(new Color(245, 245, 245));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setRange(0.0, 5.0);

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
     * Ciclo periódico: muestrea, grafica y acumula la serie temporal (Subtarea
     * #21).
     */
    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();
        double valorMuestraActiva = analogicas[canalSeleccionado];

        // 1. Alimentación de la ventana gráfica deslizante
        serieCanalActivo.add(tiempoActual, valorMuestraActiva);

        // 2. Registro en el búfer de muestras temporales [ti, Vi] para exportación
        bufferMuestrasCanalActivo.add(new double[]{tiempoActual, valorMuestraActiva});

        System.out.printf("[Tick #%d | t = %.2f s | Ts = %d ms] CH%d: %.2f V | Dig: %s%n",
                contadorTicks,
                tiempoActual,
                periodoMuestreoMs,
                (canalSeleccionado + 1),
                valorMuestraActiva,
                Arrays.toString(digitales));
    }

    /**
     * Conmuta la fuente analógica y reinicia el búfer para el nuevo canal.
     */
    public void cambiarCanalSeleccionado(int nuevoCanal) {
        if (nuevoCanal < 0 || nuevoCanal >= Data.NUM_ANALOGICAS) {
            return;
        }

        this.canalSeleccionado = nuevoCanal;

        // Limpiar el trazo gráfico
        this.serieCanalActivo.clear();
        this.serieCanalActivo.setKey("CH" + (nuevoCanal + 1));

        // Limpiar el historial en memoria para asociar muestras exclusivamente al nuevo canal
        limpiarBufferMuestras();

        String nombreCanal = "Canal " + (nuevoCanal + 1) + " - " + tipoOndaSeguro(nuevoCanal);
        this.graficoLineas.setTitle(nombreCanal);

        System.out.println(">> [Controlador] Canal conmutado a CH" + (nuevoCanal + 1) + ". Búfer temporal reiniciado.");
    }

    // =========================================================================
    // MÉTODOS DE EXTRACCIÓN Y SINCRONIZACIÓN DE BÚFER (SUBTAREA #21 / HU-04)
    // =========================================================================
    /**
     * Recupera una copia ordenada de los pares (ti, Vi) acumulados para el
     * canal activo. Retorna una lista defensiva para que Brayan la escriba en
     * disco sin interferir con el Timer.
     *
     * @return Lista de arreglos double[2] donde [0] = Tiempo (s) y [1] =
     * Voltaje (V)
     */
    public synchronized List<double[]> obtenerBufferCanalActivo() {
        synchronized (bufferMuestrasCanalActivo) {
            return new ArrayList<>(bufferMuestrasCanalActivo);
        }
    }

    /**
     * Retorna el número de muestras acumuladas en el búfer de exportación
     * actual.
     */
    public int getCantidadMuestrasBuffer() {
        return bufferMuestrasCanalActivo.size();
    }

    /**
     * Vacía el búfer temporal de muestras del canal activo.
     */
    public void limpiarBufferMuestras() {
        bufferMuestrasCanalActivo.clear();
    }

    /**
     * Retorna el canal analógico actualmente bajo monitoreo (0 a 7).
     */
    public int getCanalSeleccionado() {
        return canalSeleccionado;
    }

    /**
     * Retorna el nombre descriptivo del canal activo para cabeceras de archivo.
     */
    public String getNombreCanalActivo() {
        return tipoOndaSeguro(canalSeleccionado);
    }

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

    private String tipoOndaSeguro(int canal) {
        try {
            return modelo.getNombresAnalogicas()[canal];
        } catch (Exception e) {
            return "Señal Analógica";
        }
    }

    private void conectarEventosIniciales() {
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

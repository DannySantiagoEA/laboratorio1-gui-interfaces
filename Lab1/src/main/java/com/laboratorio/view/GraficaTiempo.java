/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.view;

import com.laboratorio.model.Senal;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Tarea #13: integración y configuración de la librería de graficación
 * (JFreeChart) para el trazado de series temporales continuas.
 *
 * Es un JPanel reutilizable: se usa una vez para la señal analógica (línea
 * continua) y otra para la señal digital (escalones). Muestra una ventana
 * deslizante con los últimos segundos de la señal, como un osciloscopio.
 */
public class GraficaTiempo extends JPanel {

    public static final double VENTANA_SEGUNDOS = 10.0; // ancho visible del eje de tiempo
    public static final int MAX_PUNTOS = 1000;          // puntos que guarda la serie

    private final XYSeries serie;       // datos (x = tiempo, y = valor)
    private final JFreeChart grafico;   // gráfico completo

    /**
     * @param ejeY texto del eje vertical, ej. "Voltaje (V)"
     * @param yMin valor mínimo del eje vertical
     * @param yMax valor máximo del eje vertical
     * @param digital true = dibuja en escalones (0/1), false = línea continua
     * @param color color de la línea
     */
    public GraficaTiempo(String ejeY, double yMin, double yMax, boolean digital, Color color) {

        // 1. Serie de datos: guarda como máximo MAX_PUNTOS (borra los más viejos)
        serie = new XYSeries("Señal");
        serie.setMaximumItemCount(MAX_PUNTOS);
        XYSeriesCollection datos = new XYSeriesCollection(serie);

        // 2. Crear el gráfico XY con la fábrica de JFreeChart
        grafico = ChartFactory.createXYLineChart(
                "", // título (se pone al escoger la señal)
                "Tiempo (s)", // eje X
                ejeY, // eje Y
                datos, // datos
                PlotOrientation.VERTICAL,
                false, // leyenda
                true, // tooltips (valor al pasar el mouse)
                false);                     // URLs

        // 3. Configurar el área de dibujo
        XYPlot plot = grafico.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // 4. Eje X (tiempo): se desplaza solo, mostrando los últimos VENTANA_SEGUNDOS
        NumberAxis ejeX = (NumberAxis) plot.getDomainAxis();
        ejeX.setAutoRange(true);
        ejeX.setFixedAutoRange(VENTANA_SEGUNDOS);

        // 5. Eje Y: rango fijo para que la gráfica no "salte"
        NumberAxis ejeVertical = (NumberAxis) plot.getRangeAxis();
        ejeVertical.setRange(yMin, yMax);
        if (digital) {
            ejeVertical.setTickUnit(new NumberTickUnit(1)); // solo marca 0 y 1
        }

        // 6. Forma de la línea: escalones para digital, continua para analógica
        XYLineAndShapeRenderer renderer = digital
                ? new XYStepRenderer()
                : new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, color);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        // 7. Meter el gráfico en un panel de Swing
        ChartPanel panelGrafico = new ChartPanel(grafico);
        panelGrafico.setPreferredSize(new Dimension(700, 260));
        setLayout(new BorderLayout());
        add(panelGrafico, BorderLayout.CENTER);
    }

    /**
     * Agrega un punto nuevo (se llama en cada ciclo de muestreo).
     */
    public void agregarPunto(double tiempo, double valor) {
        serie.add(tiempo, valor);
    }

    /**
     * Cambia la señal que se muestra: borra la serie y carga su historial
     * reciente.
     */
    public void mostrarSenal(Senal senal) {
        grafico.setTitle(senal.getNombre());
        serie.clear();
        int n = senal.getCantidad();
        int inicio = Math.max(0, n - MAX_PUNTOS);
        for (int i = inicio; i < n; i++) {
            serie.add(senal.getTiempo(i), senal.getValor(i), false); // false = no redibujar aún
        }
        serie.fireSeriesChanged();                                    // redibuja una sola vez
    }

    /**
     * Borra todos los puntos de la gráfica.
     */
    public void limpiar() {
        serie.clear();
    }
}

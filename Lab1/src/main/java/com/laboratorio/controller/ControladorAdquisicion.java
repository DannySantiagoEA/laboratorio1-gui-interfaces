/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.view.GraficaTiempo;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import com.laboratorio.model.EscritorArchivo;
import java.io.File;
import java.io.IOException;

/**
 * Controlador principal de adquisición, temporización y visualización. Orquesta
 * el flujo periódico de datos, delega el trazado en GraficaTiempo (Tarea #13) y
 * gestiona buffers y validaciones numéricas (HU-01 a HU-04).
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial estándar: 100 ms (10 Hz)
    private long contadorTicks = 0;

    // --- Componente Gráfico Reutilizable de Brayan (Tarea #13 / HU-02) ---
    private int canalSeleccionado = 0;
    private final GraficaTiempo graficaAnalogica;

    // --- Búfer de Extracción de Muestras Temporales (Subtarea #21 / HU-04) ---
    private final List<double[]> bufferMuestrasCanalActivo = Collections.synchronizedList(new ArrayList<>());

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        // Instanciación del visor analógico (0.0 V a 5.0 V, línea continua roja)
        this.graficaAnalogica = new GraficaTiempo("Voltaje (V)", Data.V_MIN, Data.V_MAX, false, Color.RED);

        // Carga inicial del nombre e historial del canal 0
        this.graficaAnalogica.mostrarSenal(this.modelo.getHistorialAnalogica(canalSeleccionado));

        configurarTimer();
        conectarEventosIniciales();
        refrescarEtiquetaTs();
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
     * Tarea periódica: muestrea el modelo, delega a GraficaTiempo y acumula el
     * búfer.
     */
    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        double tiempoActual = modelo.getTiempo();   // instante de ESTA muestra (antes de que avance)
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double valorMuestraActiva = analogicas[canalSeleccionado];

        // 1. Delegación a la gráfica de Brayan (Tarea #13 y #14)
        graficaAnalogica.agregarPunto(tiempoActual, valorMuestraActiva);

        // 2. Registro de pares (ti, Vi) en el búfer de extracción (Tarea #21)
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
     * Conmuta la señal analógica bajo monitoreo cargando su historial y título.
     */
    public void cambiarCanalSeleccionado(int nuevoCanal) {
        if (nuevoCanal < 0 || nuevoCanal >= Data.NUM_ANALOGICAS) {
            return;
        }

        this.canalSeleccionado = nuevoCanal;

        // Carga la serie histórica del nuevo canal mediante el método de Brayan
         this.graficaAnalogica.cambiarTitulo(getNombreCanalActivo());

        // Limpia el búfer temporal para asociar las nuevas muestras exclusivamente a este canal
        limpiarBufferMuestras();

        System.out.println(">> [Controlador] Canal conmutado a CH" + (nuevoCanal + 1) + ". Búfer temporal reiniciado.");
    }

    // =========================================================================
    // PARAMETRIZACIÓN Y VALIDACIÓN DEL TIEMPO DE MUESTREO (HU-03 / #17 y #18)
    // =========================================================================
    public boolean actualizarPeriodo(String entradaTexto) {
        try {
            int nuevoTs = ValidadorMuestreo.validarPeriodo(entradaTexto);
            actualizarPeriodo(nuevoTs);
            return true;
        } catch (PeriodoInvalidoException ex) {
            if (vista != null && vista.isShowing()) {
                JOptionPane.showMessageDialog(
                        vista,
                        ex.getMessage(),
                        "Tiempo de Muestreo Inválido",
                        JOptionPane.WARNING_MESSAGE
                );
            } else {
                System.err.println(">> [Validación Rechazada]: " + ex.getMessage());
            }
            return false;
        }
    }

    public void actualizarPeriodo(int nuevoPeriodoMs) {
        if (nuevoPeriodoMs < ValidadorMuestreo.MIN_MS || nuevoPeriodoMs > ValidadorMuestreo.MAX_MS) {
            System.err.println(">> [Controlador] Valor " + nuevoPeriodoMs + " ms fuera del rango permitido.");
            return;
        }

        this.periodoMuestreoMs = nuevoPeriodoMs;

        if (timerMuestreo != null) {
            timerMuestreo.setDelay(nuevoPeriodoMs);
        }
        if (modelo != null) {
            modelo.setPeriodoMuestreoMs(nuevoPeriodoMs);
        }

        refrescarEtiquetaTs();
        System.out.println(">> [Controlador] Frecuencia de muestreo reconfigurada a: " + nuevoPeriodoMs + " ms");
    }

    public void refrescarEtiquetaTs() {
        // Reservado para cuando Santiago exponga el componente en VentanaPrincipal
    }

    // =========================================================================
    // EXTRACCIÓN Y SINCRONIZACIÓN DE BÚFER (HU-04 / Subtarea #21)
    // =========================================================================
    public synchronized List<double[]> obtenerBufferCanalActivo() {
        synchronized (bufferMuestrasCanalActivo) {
            return new ArrayList<>(bufferMuestrasCanalActivo);
        }
    }

    public int getCantidadMuestrasBuffer() {
        return bufferMuestrasCanalActivo.size();
    }

    public void limpiarBufferMuestras() {
        bufferMuestrasCanalActivo.clear();
    }
    
        // =========================================================================
    // ESCRITURA EN DISCO: FORMATO VALOR VS. TIEMPO (Tarea #22)
    // =========================================================================
    /**
     * Guarda en un archivo de texto las muestras (ti, Vi) del canal activo.
     *
     * @return true si se guardó, false si hubo un problema
     */
        /**
     * Guarda en un archivo de texto la señal que se ve en la pantalla
     * visualizadora (los mismos puntos que dibuja la gráfica).
     *
     * @return true si se guardó, false si hubo un problema
     */
    public boolean guardarCanalActivo(File archivo) {
        // Se toman los puntos directamente de la gráfica: lo que se guarda es lo que se ve
        List<double[]> muestras = graficaAnalogica.getPuntosVisibles();

        if (muestras.isEmpty()) {
            notificarError("Guardar señal", "La gráfica aún no tiene puntos para guardar.");
            return false;
        }

        try {
            int escritas = EscritorArchivo.guardarValorVsTiempo(
                    muestras, archivo, graficaAnalogica.getDescripcionVisible(), "V");
            System.out.println(">> [Controlador] " + escritas + " muestras guardadas en: "
                    + archivo.getAbsolutePath());
            return true;
        } catch (IOException ex) {
            notificarError("Error al guardar", "No se pudo escribir el archivo:\n" + ex.getMessage());
            return false;
        }
    }

    /**
     * Muestra un error en una ventana si la GUI está visible, o en consola si no.
     */
    private void notificarError(String titulo, String mensaje) {
        if (vista != null && vista.isShowing()) {
            JOptionPane.showMessageDialog(vista, mensaje, titulo, JOptionPane.WARNING_MESSAGE);
        } else {
            System.err.println(">> [" + titulo + "]: " + mensaje);
        }
    }

    public int getCanalSeleccionado() {
        return canalSeleccionado;
    }

    public String getNombreCanalActivo() {
        try {
            return modelo.getNombresAnalogicas()[canalSeleccionado];
        } catch (Exception e) {
            return "Señal Analógica";
        }
    }

    /**
     * Incrusta el visor de Brayan en el contenedor Swing provisto por la Vista.
     */
    public void incrustarGrafica(JPanel contenedor) {
        if (contenedor != null) {
            contenedor.setLayout(new BorderLayout());
            contenedor.removeAll();
            contenedor.add(graficaAnalogica, BorderLayout.CENTER);
            contenedor.revalidate();
            contenedor.repaint();
        }
    }

    public GraficaTiempo getPanelGrafico() {
        return graficaAnalogica;
    }

    private void conectarEventosIniciales() {
        // Reservado para conectar botones de Santiago
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

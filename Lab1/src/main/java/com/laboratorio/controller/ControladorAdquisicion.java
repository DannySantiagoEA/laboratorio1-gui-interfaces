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

/**
 * Controlador principal de adquisición, temporización y visualización. Orquesta
 * el flujo periódico de datos, delega el trazado en GraficaTiempo (Tarea #13) y
 * gestiona buffers y validaciones numéricas (HU-01 a HU-04).
 *
 * Las partes marcadas con [AÑADIDO] conectan el controlador con la API de
 * VentanaPrincipal y con la gráfica digital: son propuesta de Santiago para
 * revisar con Danny.
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial por defecto: 100 ms (10 Hz)
    private long contadorTicks = 0;

    // --- Visor analógico (Tarea #13 / HU-02) ---
    private int canalSeleccionado = 0;
    private final GraficaTiempo graficaAnalogica;

    // --- [AÑADIDO] Visor digital ---
    private int canalDigitalSeleccionado = 0;
    private final GraficaTiempo graficaDigital;

    // --- Búfer de extracción de muestras temporales (Subtarea #21 / HU-04) ---
    // Almacena pares ordenados [ti, Vi] sincronizados con el Ts activo
    private final List<double[]> bufferMuestrasCanalActivo = Collections.synchronizedList(new ArrayList<>());

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        // Visor analógico (0.0 V a 5.0 V, línea continua roja)
        this.graficaAnalogica = new GraficaTiempo("Voltaje (V)", Data.V_MIN, Data.V_MAX, false, Color.RED);
        this.graficaAnalogica.mostrarSenal(this.modelo.getHistorialAnalogica(canalSeleccionado));

        // [AÑADIDO] Visor digital (0/1, escalones azules)
        this.graficaDigital = new GraficaTiempo("Nivel (0/1)", -0.2, 1.2, true, Color.BLUE);
        this.graficaDigital.mostrarSenal(this.modelo.getHistorialDigital(canalDigitalSeleccionado));

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
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();
        double valorMuestraActiva = analogicas[canalSeleccionado];

        // 1. Delegación a los visores (Tarea #13 y #14)
        graficaAnalogica.agregarPunto(tiempoActual, valorMuestraActiva);
        graficaDigital.agregarPunto(tiempoActual, digitales[canalDigitalSeleccionado]); // [AÑADIDO]

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

    // =========================================================================
    // SELECCIÓN DE CANAL
    // =========================================================================
    /**
     * Conmuta la señal analógica bajo monitoreo cargando su historial y título,
     * y reinicia el búfer para el nuevo canal.
     */
    public void cambiarCanalSeleccionado(int nuevoCanal) {
        if (nuevoCanal < 0 || nuevoCanal >= Data.NUM_ANALOGICAS) {
            return;
        }

        this.canalSeleccionado = nuevoCanal;

        // Carga la serie histórica del nuevo canal mediante GraficaTiempo
        this.graficaAnalogica.mostrarSenal(this.modelo.getHistorialAnalogica(nuevoCanal));

        // Limpia el búfer para asociar las nuevas muestras exclusivamente a este canal
        limpiarBufferMuestras();

        System.out.println(">> [Controlador] Canal conmutado a CH" + (nuevoCanal + 1) + ". Búfer temporal reiniciado.");
    }

    /**
     * [AÑADIDO] Conmuta la señal digital bajo monitoreo.
     */
    public void cambiarCanalDigital(int nuevoCanal) {
        if (nuevoCanal < 0 || nuevoCanal >= Data.NUM_DIGITALES) {
            return;
        }

        this.canalDigitalSeleccionado = nuevoCanal;
        this.graficaDigital.mostrarSenal(this.modelo.getHistorialDigital(nuevoCanal));

        System.out.println(">> [Controlador] Canal digital conmutado a DI" + (nuevoCanal + 1));
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

    /**
     * [AÑADIDO] Muestra en la vista el tiempo de muestreo vigente.
     */
    public void refrescarEtiquetaTs() {
        if (vista != null) {
            vista.setTiempoMuestreoActual(periodoMuestreoMs);
        }
    }

    // =========================================================================
    // EXTRACCIÓN Y SINCRONIZACIÓN DE BÚFER (HU-04 / Subtarea #21)
    // =========================================================================
    /**
     * Recupera una copia ordenada de los pares (ti, Vi) acumulados para el
     * canal activo. Retorna una lista defensiva para que se escriba en disco
     * sin interferir con el Timer.
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
        try {
            return modelo.getNombresAnalogicas()[canalSeleccionado];
        } catch (Exception e) {
            return "Señal Analógica";
        }
    }

    // =========================================================================
    // ACCESO A LOS VISORES
    // =========================================================================
    /**
     * Incrusta el visor analógico en un contenedor Swing (alternativa a
     * montarGraficaAnalogica de la vista).
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

    /**
     * [AÑADIDO] Visor de la señal digital.
     */
    public GraficaTiempo getGraficaDigital() {
        return graficaDigital;
    }

    // =========================================================================
    // EVENTOS DE LA VISTA
    // =========================================================================
    /**
     * [AÑADIDO] Conecta los controles de VentanaPrincipal con el controlador.
     */
    private void conectarEventosIniciales() {
        vista.addListenerCambiarMuestreo(e -> actualizarPeriodo(vista.getTiempoMuestreoTexto()));
        vista.addListenerCanalAnalogico(e -> cambiarCanalSeleccionado(vista.getCanalAnalogico()));
        vista.addListenerCanalDigital(e -> cambiarCanalDigital(vista.getCanalDigital()));
    }

    // =========================================================================
    // CONTROL DE LA ADQUISICIÓN
    // =========================================================================
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

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
import javax.swing.JOptionPane;

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
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();
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

        // 4. Tarea #13: agregar la muestra nueva a las gráficas de los canales seleccionados
        actualizarGraficas();
    }

    /**
     * Tarea #13: envía a cada gráfica el último punto (tiempo, valor) del canal
     * seleccionado.
     */
    private void actualizarGraficas() {
        Senal senalA = modelo.getHistorialAnalogica(canalAnalogicoSeleccionado);
        Senal senalD = modelo.getHistorialDigital(canalDigitalSeleccionado);
        int ultimaA = senalA.getCantidad() - 1;
        int ultimaD = senalD.getCantidad() - 1;

        vista.getGraficaAnalogica().agregarPunto(senalA.getTiempo(ultimaA), senalA.getValor(ultimaA));
        vista.getGraficaDigital().agregarPunto(senalD.getTiempo(ultimaD), senalD.getValor(ultimaD));
    }

    /**
     * Tarea #13: cambia la señal analógica que se grafica (0 a 7). Lo llamará el
     * menú desplegable de la GUI.
     */
    public void seleccionarCanalAnalogico(int canal) {
        modelo.reiniciarAnalogica(canal);           // valida el canal y reinicia el escalón
        canalAnalogicoSeleccionado = canal;
        vista.getGraficaAnalogica().mostrarSenal(modelo.getHistorialAnalogica(canal));
    }

    /**
     * Tarea #13: cambia la señal digital que se grafica (0 a 3). Lo llamará el
     * menú desplegable de la GUI.
     */
    public void seleccionarCanalDigital(int canal) {
        modelo.reiniciarDigital(canal);
        canalDigitalSeleccionado = canal;
        vista.getGraficaDigital().mostrarSenal(modelo.getHistorialDigital(canal));
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
        this.graficaAnalogica.mostrarSenal(this.modelo.getHistorialAnalogica(nuevoCanal));

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

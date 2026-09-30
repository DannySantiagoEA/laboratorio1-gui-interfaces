/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.model.Senal;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.Timer;
import javax.swing.JOptionPane;

/**
 * Controlador principal de adquisición y temporización. Orquesta la captura
 * periódica desacoplada de la interfaz gráfica y alimenta los canales del
 * modelo de señales.
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Periodo de muestreo base (100 ms)
    private long contadorTicks = 0;

    // Tarea #13: canal que se está graficando (0-7 analógicas, 0-3 digitales)
    private int canalAnalogicoSeleccionado = 0;
    private int canalDigitalSeleccionado = 0;

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        // Sincroniza el modelo con la tasa de refresco del controlador
        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        configurarTimer();
        conectarEventosIniciales();

        // Tarea #13: las gráficas arrancan mostrando la Analógica 1 y la Digital 1
        vista.getGraficaAnalogica().mostrarSenal(modelo.getHistorialAnalogica(canalAnalogicoSeleccionado));
        vista.getGraficaDigital().mostrarSenal(modelo.getHistorialDigital(canalDigitalSeleccionado));
    }

    /**
     * Configura el temporizador Swing para emitir ticks periódicos sin bloquear
     * la GUI.
     */
    private void configurarTimer() {
        timerMuestreo = new Timer(periodoMuestreoMs, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ejecutarCicloMuestreo();
            }
        });
    }

    /**
     * Tarea cíclica: avanza el tiempo en el modelo, captura los 12 canales y
     * muestra el flujo dinámico.
     */
    private void ejecutarCicloMuestreo() {
        contadorTicks++;

        // 1. Indicar al modelo que genere las muestras del instante t y avance el tiempo
        modelo.tomarMuestra();

        // 2. Obtener lecturas instantáneas de los 8 canales analógicos y 4 digitales
        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();

        // 3. Verificación en consola (integración del Modelo de Brayan con el Controlador de Danny)
        System.out.printf("[Tick #%d | t = %.2f s] CH1: %.2f V | CH2: %.2f V | Dig: %s%n",
                contadorTicks,
                tiempoActual,
                analogicas[0],
                analogicas[1],
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
     * Reservado para conectar los botones de la vista cuando Santiago termine
     * la GUI.
     */
    private void conectarEventosIniciales() {
                // Tarea #17: cambiar el tiempo de muestreo desde la GUI
        vista.getBtnCambiarMuestreo().addActionListener(e ->
                cambiarPeriodoDesdeTexto(vista.getTxtMuestreo().getText()));
        // En cuanto existan los botones en VentanaPrincipal:
        // vista.getBtnIniciar().addActionListener(e -> iniciarAdquisicion());
        // vista.getBtnDetener().addActionListener(e -> detenerAdquisicion());
        // Tarea #13, cuando existan los menús desplegables:
        // vista.getCmbAnalogicas().addActionListener(e -> seleccionarCanalAnalogico(vista.getCmbAnalogicas().getSelectedIndex()));
        // vista.getCmbDigitales().addActionListener(e -> seleccionarCanalDigital(vista.getCmbDigitales().getSelectedIndex()));
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

    public void actualizarPeriodo(int nuevoPeriodoMs) {
        if (nuevoPeriodoMs > 0) {
            this.periodoMuestreoMs = nuevoPeriodoMs;
            if (timerMuestreo != null) {
                timerMuestreo.setDelay(nuevoPeriodoMs);
            }
            if (modelo != null) {
                modelo.setPeriodoMuestreoMs(nuevoPeriodoMs);
            }
        }
    }

   
    public boolean cambiarPeriodoDesdeTexto(String texto) {
        try {
            int nuevoPeriodo = ValidadorMuestreo.validarPeriodo(texto);
            actualizarPeriodo(nuevoPeriodo);
            System.out.println(">> Tiempo de muestreo cambiado a " + nuevoPeriodo + " ms.");
            return true;
        } catch (PeriodoInvalidoException ex) {
            JOptionPane.showMessageDialog(vista, ex.getMessage(),
                    "Tiempo de muestreo inválido", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    public int getPeriodoMuestreoMs() {
        return periodoMuestreoMs;
    }
    
    public boolean isEjecutando() {
        return timerMuestreo != null && timerMuestreo.isRunning();
    }
}
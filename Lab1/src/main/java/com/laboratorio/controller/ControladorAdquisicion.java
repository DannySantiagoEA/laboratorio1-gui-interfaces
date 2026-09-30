/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.Timer;

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

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        // Sincroniza el modelo con la tasa de refresco del controlador
        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        configurarTimer();
        conectarEventosIniciales();
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
    }

    /**
     * Reservado para conectar los botones de la vista cuando Santiago termine
     * la GUI.
     */
    private void conectarEventosIniciales() {
        // En cuanto existan los botones en VentanaPrincipal:
        // vista.getBtnIniciar().addActionListener(e -> iniciarAdquisicion());
        // vista.getBtnDetener().addActionListener(e -> detenerAdquisicion());
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

    public boolean isEjecutando() {
        return timerMuestreo != null && timerMuestreo.isRunning();
    }
}
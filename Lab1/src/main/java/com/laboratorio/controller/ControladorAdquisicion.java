/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.view.VentanaPrincipal;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

/**
 * Controlador principal de adquisición y temporización. Orquesta la captura
 * periódica desacoplada de la interfaz gráfica (HU-01 / Tarea #10).
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Periodo base por defecto (100 ms)
    private long contadorTicks = 0;

    public ControladorAdquisicion(VentanaPrincipal vista) {
        this.vista = vista;
        configurarTimer();
        conectarEventosIniciales();
    }

    /**
     * Configura el motor de temporización Swing para emitir ticks periódicos
     * sin bloquear el hilo de despacho de eventos (EDT).
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
     * Tarea que se dispara en cada intervalo del temporizador.
     */
    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        // Verificación en consola del ciclo desacoplado (prueba unitaria de la tarea #10)
        System.out.println("[Timer Tick #" + contadorTicks + "] Muestreo ejecutado a " + periodoMuestreoMs + " ms");

        // Aquí Danny conectará más adelante la llamada al generador de Brayan (HU-02 / #14)
        // y el refresco continuo de JFreeChart.
    }

    /**
     * Enlaza el ciclo de vida del timer con los botones de control de la GUI.
     */
    private void conectarEventosIniciales() {
        // Métodos de control accesibles desde los botones o directamente desde el código
    }

    public void iniciarAdquisicion() {
        if (timerMuestreo != null && !timerMuestreo.isRunning()) {
            timerMuestreo.start();
            System.out.println(">> Adquisición iniciada.");
        }
    }

    public void detenerAdquisicion() {
        if (timerMuestreo != null && timerMuestreo.isRunning()) {
            timerMuestreo.stop();
            System.out.println(">> Adquisición detenida.");
        }
    }

    public void actualizarPeriodo(int nuevoPeriodoMs) {
        this.periodoMuestreoMs = nuevoPeriodoMs;
        if (timerMuestreo != null) {
            timerMuestreo.setDelay(nuevoPeriodoMs);
        }
    }

    public boolean isEjecutando() {
        return timerMuestreo != null && timerMuestreo.isRunning();
    }
}

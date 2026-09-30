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

public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial por defecto: 100 ms (10 Hz)
    private long contadorTicks = 0;

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        // Sincronización del paso temporal matemático con el modelo
        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

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

    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();

        System.out.printf("[Tick #%d | t = %.2f s | Ts = %d ms] CH1: %.2f V | CH2: %.2f V | Dig: %s%n",
                contadorTicks,
                tiempoActual,
                periodoMuestreoMs,
                analogicas[0],
                analogicas[1],
                Arrays.toString(digitales));
    }

    private void conectarEventosIniciales() {
        // NOTA: Se habilitará cuando Santiago maquete los botones en VentanaPrincipal
        /*
        if (vista != null && vista.getBtnActualizarTs() != null) {
            vista.getBtnActualizarTs().addActionListener(e -> procesarActualizacionTs());
        }
         */
    }

    public void procesarActualizacionTs() {
        // NOTA: Se habilitará cuando exista el campo de texto en la vista
        /*
        try {
            String texto = vista.getTxtTiempoMuestreo().getText().trim();
            int nuevoTs = Integer.parseInt(texto);
            actualizarPeriodo(nuevoTs);
        } catch (NumberFormatException ignored) {}
         */
    }

    /**
     * Reconfigura el retardo en caliente sin detener el flujo ni perder
     * muestras (Subtarea #18).
     */
    public void actualizarPeriodo(int nuevoPeriodoMs) {
        if (nuevoPeriodoMs > 0) {
            this.periodoMuestreoMs = nuevoPeriodoMs;

            // 1. Modificación del retardo en tiempo real
            if (timerMuestreo != null) {
                timerMuestreo.setDelay(nuevoPeriodoMs);
            }

            // 2. Actualización de la variable interna de paso temporal en el modelo
            if (modelo != null) {
                modelo.setPeriodoMuestreoMs(nuevoPeriodoMs);
            }

            // 3. Refresco informativo
            refrescarEtiquetaTs();

            System.out.println(">> [Controlador] Frecuencia de muestreo reconfigurada a: " + nuevoPeriodoMs + " ms");
        }
    }

    private void refrescarEtiquetaTs() {
        // NOTA: Se habilitará cuando exista la etiqueta en la vista
        /*
        if (vista != null && vista.getLblTsActual() != null) {
            vista.getLblTsActual().setText("Ts actual: " + periodoMuestreoMs + " ms");
        }
         */
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

    public boolean isEjecutando() {
        return timerMuestreo != null && timerMuestreo.isRunning();
    }
}

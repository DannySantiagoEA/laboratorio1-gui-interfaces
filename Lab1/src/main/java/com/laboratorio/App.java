/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio;

import com.laboratorio.controller.ControladorAdquisicion;
import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;

/**
 * Punto de entrada principal de la aplicación. Configura el aspecto visual
 * nativo del sistema operativo e inicializa el flujo MVC dentro del Event
 * Dispatch Thread (EDT)[cite: 1, 14].
 */
public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal vista = new VentanaPrincipal();
            Data modelo = new Data();
            ControladorAdquisicion controlador = new ControladorAdquisicion(vista, modelo);

            vista.setTitle("Laboratorio 1 - Adquisición de Señales e Instrumentación (Interfaces)");
            vista.setLocationRelativeTo(null); // Centrar en la pantalla
            vista.setVisible(true);

            // Iniciar flujo de adquisición periódica
            controlador.iniciarAdquisicion();
        });
    }
}

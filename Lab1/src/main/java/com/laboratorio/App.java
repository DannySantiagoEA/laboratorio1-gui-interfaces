/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio;

import com.laboratorio.controller.ControladorAdquisicion;
import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.BorderLayout;
import java.util.Scanner;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

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

            // Montar la gráfica de JFreeChart en la ventana
            vista.setLayout(new BorderLayout());
            vista.add(controlador.getPanelGrafico(), BorderLayout.CENTER);
            vista.setSize(800, 500);
            vista.setLocationRelativeTo(null);
            vista.setVisible(true);

            controlador.iniciarAdquisicion();

            // Consola para conmutar canales del 1 al 8 en caliente
            Thread listenerCanales = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                System.out.println("\n============================================================");
                System.out.println("  PRUEBA SUBTAREA #14 - CONMUTACIÓN DE CANALES (CH1 a CH8)");
                System.out.println("  Escribe un canal del 1 al 8 y pulsa ENTER para cambiar:");
                System.out.println("============================================================\n");

                while (true) {
                    if (scanner.hasNextLine()) {
                        String entrada = scanner.nextLine().trim();
                        try {
                            int canal = Integer.parseInt(entrada);
                            if (canal >= 1 && canal <= 8) {
                                // Conmuta el canal en el Event Dispatch Thread
                                SwingUtilities.invokeLater(() -> controlador.cambiarCanalSeleccionado(canal - 1));
                            } else {
                                System.err.println(">> Ingrese un canal entre 1 y 8.");
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
            listenerCanales.setDaemon(true);
            listenerCanales.start();
        });
    }
}

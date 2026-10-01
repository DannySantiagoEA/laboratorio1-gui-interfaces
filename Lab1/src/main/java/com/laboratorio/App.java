/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio;

import com.laboratorio.controller.ControladorAdquisicion;
import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.BorderLayout;
import java.util.List;
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

            vista.setLayout(new BorderLayout());
            vista.add(controlador.getPanelGrafico(), BorderLayout.CENTER);
            vista.setSize(800, 500);
            vista.setLocationRelativeTo(null);
            vista.setVisible(true);

            controlador.iniciarAdquisicion();

            // Consola para conmutar canales o extraer el buffer de pares (ti, Vi)
            Thread listenerConsola = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                System.out.println("\n============================================================");
                System.out.println("  CONSOLA - PRUEBA SUBTAREA #21 (EXTRACCIÓN DE BUFFER)");
                System.out.println("  • Escribe 'buffer' para extraer los pares (ti, Vi) actuales.");
                System.out.println("  • Escribe un número (1 al 8) para cambiar de canal.");
                System.out.println("============================================================\n");

                while (true) {
                    if (scanner.hasNextLine()) {
                        String entrada = scanner.nextLine().trim();

                        if (entrada.equalsIgnoreCase("buffer")) {
                            // Extrae la lista de muestras temporales sincronizadas
                            List<double[]> muestras = controlador.obtenerBufferCanalActivo();
                            System.out.println("\n>>> [SUBTAREA #21] EXTRACCIÓN DEL BUFFER DEL CANAL "
                                    + (controlador.getCanalSeleccionado() + 1) + " (" + controlador.getNombreCanalActivo() + ")");
                            System.out.println(">>> Total de muestras en memoria: " + muestras.size());
                            System.out.println("------------------------------------------------------------");
                            System.out.println("Fila\tTiempo ti (s)\tVoltaje Vi (V)");
                            System.out.println("------------------------------------------------------------");

                            // Imprimir los primeros 10 puntos recuperados como evidencia
                            int limite = Math.min(muestras.size(), 10);
                            for (int i = 0; i < limite; i++) {
                                double[] punto = muestras.get(i);
                                System.out.printf("[%d]\tti = %.3f s\tVi = %.3f V%n", i + 1, punto[0], punto[1]);
                            }

                            if (muestras.size() > 10) {
                                System.out.println("... (" + (muestras.size() - 10) + " muestras adicionales en memoria)");
                            }
                            System.out.println("------------------------------------------------------------\n");

                        } else {
                            try {
                                int canal = Integer.parseInt(entrada);
                                if (canal >= 1 && canal <= 8) {
                                    SwingUtilities.invokeLater(() -> controlador.cambiarCanalSeleccionado(canal - 1));
                                } else {
                                    System.err.println(">> Canal inválido. Ingrese un número entre 1 y 8.");
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            });
            listenerConsola.setDaemon(true);
            listenerConsola.start();
        });
    }
}

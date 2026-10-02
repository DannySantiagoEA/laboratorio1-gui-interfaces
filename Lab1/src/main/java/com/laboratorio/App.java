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
import java.io.File;

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

            // Montaje del lienzo de JFreeChart directamente en la ventana (JFrame)
            vista.setLayout(new BorderLayout());
            vista.add(controlador.getPanelGrafico(), BorderLayout.CENTER);
            vista.setSize(850, 520);
            vista.setLocationRelativeTo(null);
            vista.setVisible(true);

            controlador.iniciarAdquisicion();

            // Hilo de consola interactivo para pruebas de las tareas integradas
            Thread hiloConsola = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                System.out.println("\n============================================================");
                System.out.println("       BANCO DE PRUEBAS DE INTEGRACIÓN (APP CONSOLE)         ");
                System.out.println("============================================================");
                System.out.println(" COMANDOS DISPONIBLES:");
                System.out.println("  • 'ch 1' al 'ch 8' : Conmuta el canal analógico (#14)");
                System.out.println("  • 'buffer'         : Extrae los pares (ti, Vi) acumulados (#21)");
                 System.out.println("  • 'guardar [ruta]' : Escribe el canal activo en disco (#22)");
                System.out.println("                       Ej: 'guardar', 'guardar D:/datos/seno.txt'");
                System.out.println("  • Cualquier texto  : Prueba sanitizacion y validacion de Ts (#17 y #18)");
                System.out.println("                       Ej: '5', '50 ms', '1 000', 'abc', '50,5'");
                System.out.println("============================================================\n");

                while (true) {
                    if (scanner.hasNextLine()) {
                        String entrada = scanner.nextLine().trim();
                        if (entrada.isEmpty()) {
                            continue;
                        }

                        if (entrada.equalsIgnoreCase("buffer")) {
                            List<double[]> muestras = controlador.obtenerBufferCanalActivo();
                            int canal = controlador.getCanalSeleccionado() + 1;
                            String nombre = controlador.getNombreCanalActivo();

                            System.out.println("\n------------------------------------------------------------");
                            System.out.println(">>> [BÚFER TEMPORAL] CANAL CH" + canal + " (" + nombre + ")");
                            System.out.println(">>> Muestras en memoria: " + muestras.size());
                            System.out.println("------------------------------------------------------------");
                            System.out.println("Fila\tTiempo ti (s)\tVoltaje Vi (V)");
                            System.out.println("------------------------------------------------------------");

                            int limite = Math.min(muestras.size(), 10);
                            for (int i = 0; i < limite; i++) {
                                double[] punto = muestras.get(i);
                                System.out.printf("[%d]\tti = %.3f s\tVi = %.3f V%n", i + 1, punto[0], punto[1]);
                            }
                            if (muestras.size() > 10) {
                                System.out.println("... (" + (muestras.size() - 10) + " muestras adicionales)");
                            }
                            System.out.println("------------------------------------------------------------\n");
                            
                        } else if (entrada.toLowerCase().startsWith("guardar")) {
                           String ruta = entrada.substring("guardar".length()).trim();
                           if (ruta.isEmpty()) {
                               ruta = "senal_CH" + (controlador.getCanalSeleccionado() + 1) + ".txt";
                           }
                           File archivo = new File(ruta);
                           SwingUtilities.invokeLater(() -> controlador.guardarCanalActivo(archivo));
                           
                        } else if (entrada.toLowerCase().startsWith("ch ") || entrada.matches("^[1-8]$")) {
                            String numStr = entrada.toLowerCase().replace("ch", "").trim();
                            int canalIndex = Integer.parseInt(numStr) - 1;
                            SwingUtilities.invokeLater(() -> controlador.cambiarCanalSeleccionado(canalIndex));

                        } else {
                            SwingUtilities.invokeLater(() -> controlador.actualizarPeriodo(entrada));
                        }
                    }
                }
            });

            hiloConsola.setDaemon(true);
            hiloConsola.start();
        });
    }
}

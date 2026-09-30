/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio;

import com.laboratorio.controller.ControladorAdquisicion;
import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
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

            vista.setLocationRelativeTo(null);
            vista.setVisible(true);

            // Iniciar a 100 ms por defecto
            controlador.iniciarAdquisicion();

            // Hilo secundario para inyectar valores de prueba desde la consola
            Thread listenerConsola = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                System.out.println("\n>>> PRUEBA SUBTAREA #18: Escribe un nuevo Ts (ej: 50, 500, 1000) y pulsa ENTER:");
                while (true) {
                    if (scanner.hasNextLine()) {
                        try {
                            int nuevoTs = Integer.parseInt(scanner.nextLine().trim());
                            SwingUtilities.invokeLater(() -> controlador.actualizarPeriodo(nuevoTs));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
            listenerConsola.setDaemon(true);
            listenerConsola.start();
        });
    }
}

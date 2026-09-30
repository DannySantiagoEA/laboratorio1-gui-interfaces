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

public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal vista = new VentanaPrincipal();
            Data modelo = new Data(); // Instancia del modelo de señales
            ControladorAdquisicion controlador = new ControladorAdquisicion(vista, modelo);

            vista.setLocationRelativeTo(null);
            vista.setVisible(true);

            controlador.iniciarAdquisicion();
        });
    }
}
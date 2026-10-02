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
            Data modelo = new Data();
            VentanaPrincipal vista = new VentanaPrincipal();
            vista.cargarNombresCanales(modelo.getNombresAnalogicas(), modelo.getNombresDigitales());

            ControladorAdquisicion controlador = new ControladorAdquisicion(vista, modelo);
            vista.montarGraficaAnalogica(controlador.getPanelGrafico());
            vista.montarGraficaDigital(controlador.getGraficaDigital());

            vista.setLocationRelativeTo(null);
            vista.setVisible(true);
            controlador.iniciarAdquisicion();
        });
    }
}

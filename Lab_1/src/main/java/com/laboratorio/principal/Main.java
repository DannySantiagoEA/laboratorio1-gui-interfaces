/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.principal;


import javax.swing.SwingUtilities;
import com.laboratorio.modelo.Data;
import com.laboratorio.vista.VentanaPrincipal;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Data modelo = new Data();                      // 1. los datos
            VentanaPrincipal ventana = new VentanaPrincipal(modelo); // 2. la pantalla
            ventana.setVisible(true);                                // 3. mostrarla
        });
    }
}
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Tarea #22: rutina de escritura en disco (BufferedWriter/FileWriter) con el
 * formato Valor vs. Tiempo.
 *
 * Ejemplo del archivo que se genera:
 *
 * # Señal: Analógica 1 - Seno
 * # Formato: Valor vs. Tiempo
 * # Muestras: 3
 * Tiempo(s)    Valor(V)
 * 0.100        2.5000
 * 0.200        3.9695
 * 0.300        4.8776
 */
public class EscritorArchivo {

    /**
     * Escribe las muestras (ti, Vi) en un archivo de texto.
     *
     * @param muestras    lista de pares {tiempo, valor}
     * @param archivo     archivo destino (se crea o se sobrescribe)
     * @param nombreSenal nombre de la señal, va en el encabezado
     * @param unidad      unidad del valor, ej. "V"
     * @return cantidad de muestras escritas
     * @throws IOException si no se puede crear o escribir el archivo
     */
    public static int guardarValorVsTiempo(List<double[]> muestras, File archivo,
            String nombreSenal, String unidad) throws IOException {

        // try-with-resources: cierra el archivo automáticamente, aunque ocurra un error
        try (BufferedWriter escritor = new BufferedWriter(
                new FileWriter(archivo, StandardCharsets.UTF_8))) {

            // 1. Encabezado
            escritor.write("# Señal: " + nombreSenal);
            escritor.newLine();
            escritor.write("# Formato: Valor vs. Tiempo");
            escritor.newLine();
            escritor.write("# Muestras: " + muestras.size());
            escritor.newLine();
            escritor.write("Tiempo(s)\tValor(" + unidad + ")");
            escritor.newLine();

            // 2. Una línea por muestra. Locale.US -> punto decimal (2.5000 y no 2,5000)
            for (double[] punto : muestras) {
                escritor.write(String.format(Locale.US, "%.3f\t%.4f", punto[0], punto[1]));
                escritor.newLine();
            }
        }
        return muestras.size();
    }
}
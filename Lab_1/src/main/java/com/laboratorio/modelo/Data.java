/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.modelo;



/**
 * Tarea #8: estructuras de datos en memoria para
 * 8 canales analógicos y 4 canales digitales.
 * Cada canal tiene su propia función y todos usan la misma frecuencia.
 */
public class Data {

    public static final int NUM_ANALOGICAS = 8;
    public static final int NUM_DIGITALES = 4;
    public static final double V_MIN = 0.0;    // rango de las analógicas
    public static final double V_MAX = 5.0;
    public static final double UMBRAL = 2.5;   // V: por encima es 1, por debajo es 0

    private double frecuencia = 1.0;           // Hz: un solo valor para todas las señales

    // 1. Estado instantáneo: el valor ACTUAL de cada canal
    private double[] analogicas = new double[NUM_ANALOGICAS];
    private int[] digitales = new int[NUM_DIGITALES];          // 0 o 1

    // 2. Historial de cada canal (para graficar y guardar en archivo más adelante)
    private Senal[] historialAnalogicas = new Senal[NUM_ANALOGICAS];
    private Senal[] historialDigitales = new Senal[NUM_DIGITALES];

    // 3. Un generador de funciones por canal
    private GeneradorFuncion[] generadoresAnalogicos = new GeneradorFuncion[NUM_ANALOGICAS];
    private GeneradorFuncion[] generadoresDigitales = new GeneradorFuncion[NUM_DIGITALES];

    // 4. Nombres que aparecen en los menús desplegables
    private String[] nombresAnalogicas = new String[NUM_ANALOGICAS];
    private String[] nombresDigitales = new String[NUM_DIGITALES];

    // 5. Tiempo
    private int periodoMuestreoMs = 50;    // tiempo de muestreo en milisegundos
    private double tiempo = 0;             // tiempo transcurrido en segundos

    public Data() {
        for (int i = 0; i < NUM_ANALOGICAS; i++) {
            String tipo = TipoOnda.ANALOGICAS[i];
            nombresAnalogicas[i] = "Analógica " + (i + 1) + " - " + tipo;
            historialAnalogicas[i] = new Senal(nombresAnalogicas[i]);
            generadoresAnalogicos[i] = new GeneradorFuncion(tipo, frecuencia);
        }
        for (int i = 0; i < NUM_DIGITALES; i++) {
            String tipo = TipoOnda.DIGITALES[i];
            nombresDigitales[i] = "Digital " + (i + 1) + " - " + tipo;
            historialDigitales[i] = new Senal(nombresDigitales[i]);
            generadoresDigitales[i] = new GeneradorFuncion(tipo, frecuencia);
        }
    }

    /** Lee una muestra nueva en los 12 canales, la guarda y avanza el tiempo. */
    public void tomarMuestra() {
        for (int i = 0; i < NUM_ANALOGICAS; i++) {
            double v = generadoresAnalogicos[i].valor(tiempo);
            analogicas[i] = Math.max(V_MIN, Math.min(V_MAX, v));    // limita a 0 - 5 V
            historialAnalogicas[i].agregarMuestra(tiempo, analogicas[i]);
        }
        for (int i = 0; i < NUM_DIGITALES; i++) {
            // Comparador: si la función supera el umbral vale 1, si no vale 0
            digitales[i] = (generadoresDigitales[i].valor(tiempo) >= UMBRAL) ? 1 : 0;
            historialDigitales[i].agregarMuestra(tiempo, digitales[i]);
        }
        tiempo += periodoMuestreoMs / 1000.0;
    }

    // ---------- Al escoger una señal en el menú, instantaneamente ----------
    public void reiniciarAnalogica(int canal) {
        validar(canal, NUM_ANALOGICAS);
        generadoresAnalogicos[canal].reiniciar(tiempo);
    }

    public void reiniciarDigital(int canal) {
        validar(canal, NUM_DIGITALES);
        generadoresDigitales[canal].reiniciar(tiempo);
    }

    // ---------- Nombres para los menús ----------
    public String[] getNombresAnalogicas() { return nombresAnalogicas; }
    public String[] getNombresDigitales() { return nombresDigitales; }

    // ---------- Valores actuales ----------
    public double getAnalogica(int canal) {
        validar(canal, NUM_ANALOGICAS);
        return analogicas[canal];
    }

    public int getDigital(int canal) {
        validar(canal, NUM_DIGITALES);
        return digitales[canal];
    }

    // ---------- Historiales ----------
    public Senal getHistorialAnalogica(int canal) {
        validar(canal, NUM_ANALOGICAS);
        return historialAnalogicas[canal];
    }

    public Senal getHistorialDigital(int canal) {
        validar(canal, NUM_DIGITALES);
        return historialDigitales[canal];
    }

    // ---------- Frecuencia ----------
    public double getFrecuencia() { return frecuencia; }

    // ---------- Tiempo ----------
    public double getTiempo() { return tiempo; }
    public int getPeriodoMuestreoMs() { return periodoMuestreoMs; }

    public void setPeriodoMuestreoMs(int ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("El tiempo de muestreo debe ser mayor que 0");
        }
        periodoMuestreoMs = ms;
    }

    /** Evita pedir un canal que no existe (ej. analógica 9). */
    private void validar(int canal, int limite) {
        if (canal < 0 || canal >= limite) {
            throw new IndexOutOfBoundsException(
                    "El canal " + canal + " no existe (válidos: 0 a " + (limite - 1) + ")");
        }
    }
}
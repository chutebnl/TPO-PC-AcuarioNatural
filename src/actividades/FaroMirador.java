package actividades;

import hilos.AdminFaroMirador;
import hilos.Reloj;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Faro-Mirador con vista a 40 m de altura y descenso en tobogán: Admira desde lo alto to-do el
 * esplendor de una maravilla natural y desciende en tobogán hasta una pileta. Para acceder al tobogán
 * es necesario subir por una escalera caracol, que tiene capacidad para n personas. Al llegar a la cima
 * nos encontraremos con dos toboganes para descender, la elección del tobogán es realizada por un
 * administrador de cola que indica que persona de la fila va a un tobogán y cuál va al otro. Es
 * importante destacar que una persona no se tira por el tobogán hasta que la anterior no haya llegado a
 * la pileta, es decir, sobre cada tobogán siempre hay a lo sumo una persona.
 */
public class FaroMirador implements Actividad {
    private boolean abierto;
    private int capacidadEscalera, capacidadMirador, quieren_tirarse, queTobogan, subiendo, mirando;
    private boolean disponibleToboganA, disponibleToboganB;
    private Lock lock;
    private Condition subir, mirar, tirarse, administrar, toboganA, toboganB;

    public FaroMirador(int capacidadEscalera, int capacidadMirador) {
        this.abierto = false;
        this.capacidadEscalera = capacidadEscalera;
        this.capacidadMirador = capacidadMirador;
        queTobogan = 0;
        quieren_tirarse = 0;
        subiendo = 0;
        mirando = 0;
        lock = new ReentrantLock(true);
        subir = lock.newCondition();
        mirar = lock.newCondition();
        tirarse = lock.newCondition();
        administrar = lock.newCondition();
        toboganA = lock.newCondition();
        toboganB = lock.newCondition();

        disponibleToboganA = true;
        disponibleToboganB = true;

        Thread admin = new Thread(new AdminFaroMirador(this), "adminFaroMirador");
        admin.start();
    }

    @Override
    public void abrir() {
        lock.lock();
        abierto = true;
        subir.signal();
        lock.unlock();
    }

    /**
     * Cuando cierre el parque y hay gente en la fila no pueden entrar, se retiran.
     * Si pudo entrar y la escalera está llena, espera a que se libere.
     * @return si pudo entrar al FaroMirador.
     */
    @Override
    public boolean entrar() {
        boolean pudoEntrar = false; // para retornar si pudo entrar al FaroMirador
        lock.lock();
        try {
            // si la escalera esta llena espera afuera
            while (subiendo == capacidadEscalera && abierto) {
                subir.await();
            }
            pudoEntrar = abierto;
            if (pudoEntrar)
                subiendo++;
            lock.unlock();

            if (pudoEntrar) {
                System.out.println(Thread.currentThread().getName() + " subiendo escalera");
                Thread.sleep(Reloj.DURACION_MIN * 10);
            }
        } catch (InterruptedException ignored) {
        }
        return pudoEntrar;
    }

    /**
     * El visitante admira la vista desde la cima del faro, si está lleno espera a que haya lugar.
     */
    public void admirarVista() {
        lock.lock();
        try {
            // espera mientras esté lleno el mirador
            while (mirando == capacidadMirador) {
                mirar.await();
            }
            mirando++;
            subiendo--;
            subir.signal();
            lock.unlock();
            System.out.println(Thread.currentThread().getName() + " admirando vista");
            Thread.sleep(Reloj.DURACION_MIN * 15);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * El visitante espera a que el admin le asigne un tobogán y luego se tira.
     */
    public void desenderPorTobogan() {
        int cualTobogan;
        lock.lock();
        try {
            quieren_tirarse++;
            administrar.signal();
            System.out.println(Thread.currentThread().getName() + " quiere tirarse");
            // espera a que el admin le asigne un tobogan
            while (queTobogan == 0) {
                tirarse.await();
            }
            // se tira por el tobogan que le indicó el administrador con cualTobogan
            quieren_tirarse--;
            mirando--;
            mirar.signal(); // para que los que estan en la escalera puedan subir al mirador
            cualTobogan = queTobogan;
            queTobogan = 0; // para que no se tire otro
            switch (cualTobogan) {
                case 1:
                    while (!disponibleToboganA) {
                        toboganA.await();
                    }
                    disponibleToboganA = false;
                    break;
                case 2:
                    while (!disponibleToboganB) {
                        toboganB.await();
                    }
                    disponibleToboganB = false;
                    break;
            }
            lock.unlock();

            System.out.println(Thread.currentThread().getName() + " desendiendo por tobogan " + cualTobogan + "...");
            Thread.sleep(Reloj.DURACION_MIN * 3); // 3 minutos en desender
            System.out.println(Thread.currentThread().getName() + " salio por tobogan " + cualTobogan);

            // libera el tobogan y le dice al admin que ya se tiro
            lock.lock();
            if (cualTobogan == 1) {
                disponibleToboganA = true;
            } else if (cualTobogan == 2) {
                disponibleToboganB = true;
            }
            administrar.signal();
            lock.unlock();
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Método utilizado por el arministrador de los toboganes.
     * El admin espera mientras nadie se quiera tirar o ningún tobogán esté disponible.
     * Luego le indica al visitante por cual tobogán tirarse.
     */
    public void administrar() {
        lock.lock();
        try {
            while (quieren_tirarse == 0 || (!disponibleToboganA && !disponibleToboganB)) {
                administrar.await();
            }
            if (disponibleToboganA) {
                queTobogan = 1;
            } else {
                queTobogan = 2;
            }
            tirarse.signal();
        } catch (InterruptedException ignored){
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void salir() {
        System.out.println(Thread.currentThread().getName() + " salio");
    }

    /**
     *  Para que entre nadie si esta cerrado y los que estan en la fila de la escalera se van.
     */
    @Override
    public void cerrar() {
        lock.lock();
        abierto = false;
        subir.signal();
        lock.unlock();
    }

    @Override
    public boolean isAbierto() {
        return abierto;
    }
}

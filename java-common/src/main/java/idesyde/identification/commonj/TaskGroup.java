package idesyde.identification.commonj;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;
public class TaskGroup {
    private Timer timer;

    public TaskGroup(Timer timer) {
        this.timer = timer;
        // Schedule a series of tasks with different delay.
        this.timer.schedule(new SimpleTask("A"), 1000);
        this.timer.schedule(new SimpleTask("B"), 5000);
        this.timer.schedule(new SimpleTask("C"), 3000);
        this.timer.schedule(new SimpleTask("D"), 1500);
        this.timer.schedule(new SimpleTask("E"), 10000);
        this.timer.schedule(new SimpleTask("F"), 2000);
        this.timer.schedule(new SimpleTask("G"), 6000);
        this.timer.schedule(new SimpleTask("H"), 3000);
        this.timer.schedule(new SimpleTask("I"), 100);
        this.timer.schedule(new SimpleTask("J"), 8000);
    }

    private class SimpleTask extends TimerTask {
        private String id;

        public SimpleTask(String id) {
            this.id = id;
        }

        @Override
        public void run() {
            System.out.println("Task " + this.id + " has been completed.");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Timer timer = new Timer();
        new TaskGroup(timer);
        // Wait for all the tasks to finish.
       // Thread.sleep(15000);
        //timer.cancel();
    }
}

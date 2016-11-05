package com.github.haringat.oc.v8.eventloop;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Locker;

import java.util.*;

public class EventLoop {

    private final List<Task> tasks = new ArrayList<Task>();
    private List<ScheduledTask> scheduledTasks = new ArrayList<ScheduledTask>();
    private boolean active = true;
    private final V8 v8;
    private Thread v8Thread;

    public EventLoop(V8 v8) {
        this.v8 = v8;
        final EventLoop _this = this;
        this.v8Thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (_this.active) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        synchronized (_this.tasks) {
                            synchronized (_this.v8) {
                                V8Locker locker = _this.v8.getLocker();
                                locker.acquire();
                                for (Task task : tasks) {
                                    task.execute();
                                }
                                locker.release();
                            }
                        }
                    }
                }
            }
        });
        this.v8Thread.start();
    }

    public int schedule(final Task task, long timeout) {
        final EventLoop _this = this;
        Timer timer = new Timer();
        this.scheduledTasks.add(new ScheduledTask(task, timer));
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (_this.tasks) {
                    _this.tasks.add(task);
                    _this.v8Thread.interrupt();
                }
            }
        }, timeout);
        return this.scheduledTasks.size() - 1;
    }

    public int scheduleRepetitive(final Task task, long timeout) {
        final EventLoop _this = this;
        Timer timer = new Timer();
        this.scheduledTasks.add(new ScheduledTask(task, timer));
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (_this.tasks) {
                    _this.tasks.add(task);
                    _this.v8Thread.interrupt();
                }
            }
        }, timeout, timeout);
        return this.scheduledTasks.size() - 1;
    }

    public void cancelScheduledTask(int handle) {
        this.scheduledTasks.get(handle).cancel();
    }

    public Object execute(Task task) {
        Object result;
        synchronized (this.v8) {
            this.v8.getLocker().acquire();
            result = task.execute();
            this.v8.getLocker().release();
        }
        return result;
    }

    public void shutDown() {
        this.active = false;
        for (ScheduledTask task: this.scheduledTasks) {
            task.cancel();
        }
    }

    public V8 getV8() {
        return this.v8;
    }

}
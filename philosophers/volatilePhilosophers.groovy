class Chopstick {
    private static int count = 0;
    private int idx = 0 
    volatile Object occupied = null
    
    public Chopstick() {
        this.idx = count++;
    }
    
    public String toString() {
        "Chopstick #$idx"
    }
}

def names = ['Confucius'
    , 'Kant'
    , 'Nizche'
    , 'Socrates'
    , 'Diogenes'
]

def stickCount = 5
def sticks = (0..4).collect {
    new Chopstick()
}

def threadsInCriticalSection = new java.util.concurrent.atomic.AtomicInteger(0)
def waitCountSamples = new java.util.concurrent.CopyOnWriteArrayList<Integer>()
def waitingThreadsSamples = new java.util.concurrent.CopyOnWriteArrayList<Integer>()
def waitingThreads = new java.util.concurrent.atomic.AtomicInteger(0)
def iterationsToThreadMap = [:]
def waitToThreadMap = [:]

def threads = (0..4).collect { i ->
    final left = sticks[i]
    final right = sticks[i == 4 ? 0 : i + 1]
    iterationsToThreadMap[names[i]] = new java.util.concurrent.atomic.AtomicInteger(0) 
    waitToThreadMap[names[i]] = new java.util.concurrent.atomic.AtomicInteger(0) 
    Thread.startDaemon(names[i]) {
        def myName = Thread.currentThread().getName()
        println "$left <-- ${String.format('%-10s', myName)} --> $right"
        def iterations = iterationsToThreadMap[myName]
        def waitOverWork = waitToThreadMap[myName]
        int waitCount = 0
        try {
            while (!Thread.currentThread().isInterrupted()) {
                iterations.incrementAndGet()
                //println "$myName starving"
                waitingThreadsSamples.add(waitingThreads.incrementAndGet())
                waitCount++
                waitOverWork.incrementAndGet()                
                if (left.occupied == null && (left.occupied = myName) == myName) {
                    int doublecheck = 2                
                    while (!Thread.currentThread().isInterrupted() && left.occupied == myName) {
                        if (doublecheck-- > 0) {
                            continue
                        }
                        else if (left.occupied == myName && right.occupied == null && (right.occupied = myName) == myName) {
                            doublecheck = 2
                            while (!Thread.currentThread().isInterrupted() && right.occupied == myName && doublecheck-- > 0) {
                                ; //spin
                            }

                            if (left.occupied == myName && right.occupied == myName) {
                                int criticalSectionCheck = threadsInCriticalSection.incrementAndGet()
                                if (criticalSectionCheck > stickCount/2) { //depends on number of sticks
                                    threadsInCriticalSection.decrementAndGet()
                                    sticks.each {
                                        println "$it : $it.occupied"
                                    }
                                    println "_______________"
                                    throw new IllegalStateException("$myName; $left: ${left.occupied}; $right: ${right.occupied}; too many eaters: " + criticalSectionCheck)
                                }
                                //println "$myName eating"
                                waitCountSamples.add(waitCount)
                                waitingThreadsSamples.add(waitingThreads.decrementAndGet())
                                waitOverWork.decrementAndGet()
                                waitCount = 0
                                threadsInCriticalSection.decrementAndGet()
                                right.occupied = null
                                left.occupied = null
                                Thread.sleep(1) //digestion; comment this line to see some starved philosophers
                            }
                        }
                    }
                }
            }
        }
        catch (InterruptedException ie) {
            Thread.interrupted()
        }
        catch (Exception ex) {
            ex.printStackTrace()
            if (left.occupied == myName) {
                left.occupied = null
            }
            if (right.occupied == myName) {
                right.occupied = null
            }
        }
    }
}

Thread.sleep(30000)
threads.each {
    it.interrupt()
}

Thread.sleep(100)
sticks.each {
    println "$it : $it.occupied"
}
println ""
println ""
println String.format("%-11s|%-11s|%-11s|%-11s|%-12s", 'Philosopher', 'total', 'successful', 'unsuccesful', 'u/t ratio')
println "------------------------------------------------------------"
int starvedPhilosophers = 0
def maxWaits = waitCountSamples.max()
def iterationsSamples = iterationsToThreadMap.values().collect{ it.get() }
int maxSuccessful = 0
names.each {
    int total = iterationsToThreadMap[it].get()
    int lost = waitToThreadMap[it]
    if (maxSuccessful < total - lost) {
        maxSuccessful = total - lost
    }    
}
names.each {
    int total = iterationsToThreadMap[it].get()
    int lost = waitToThreadMap[it]
    int successful = total - lost
    if (total == lost || successful/maxSuccessful < 0.1) {
        starvedPhilosophers++
        println String.format("%-11s|%-11s|%-11s|%-11s|%-12s<----------starved", it, total, successful, lost, lost/total)
    }
    else {
        println String.format("%-11s|%-11s|%-11s|%-11s|%-12s", it, total, successful, lost, lost/total)
    }
}
println "----------"
println starvedPhilosophers > 0 ? "$starvedPhilosophers philosopher${ starvedPhilosophers > 1 ? 's' : ''} starved" : 'nobody starved'
println "----------"
if (!waitCountSamples.isEmpty()) {
    println "Waits per thread;"// $waitCountSamples"
    println "Max: $maxWaits"
    println "Avg: ${waitCountSamples.sum()/waitCountSamples.size()}"
    println "Last 15: ${waitCountSamples.size() > 15 ? waitCountSamples.subList(waitCountSamples.size() - 16, waitCountSamples.size() - 1) : waitCountSamples}"
    println "----------"
}
if (!waitingThreadsSamples.isEmpty()) {
    def maxWaiting = waitingThreadsSamples.max() 
    println "Starve/eat balance;"// $waitingThreadsSamples"
    println "Min: ${waitingThreadsSamples.min()}"
    println "Max: $maxWaiting"
    println "Waits to iterations ratio: ${iterationsSamples.size() > 0 ? maxWaiting/iterationsSamples.sum() : 'no data'}"
    println "Last 15: ${waitingThreadsSamples.size() > 15 ? waitingThreadsSamples.subList(waitingThreadsSamples.size() - 16, waitingThreadsSamples.size() - 1) : waitingThreadsSamples}"
    println "----------"                        
}
println "Experiment finished"
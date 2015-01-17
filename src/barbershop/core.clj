(ns barbershop.core
  (:require [clojure.core.async :as async]))

;; Channels are like queues; they're decoupled from producer and consumer.
;; Channels are USUALLY FIFO. But, that's an implementation point,
;; & not guaranteed for any given channel.
;; go blocks are decoupled from the running thread when blocked on reading
;; or writing to channels.
;; ANYONE can write or read from any channel!
;; In Golang, you can type a channel as read-only or write-only. It's best to
;; treat channels this way.
;; A go loop won't quit until you tell it to.
;; Dedicated channels for quitting are useful.
;; If you want to wait/sleep/..., there's a pattern: (<! (timeout N))
;; Testing is challenging because you don't want a thread to sleep indefinitely.
;;   But, there's a trick; deadlocks OFTEN come from threads waiting to read
;;   from a channel.
;;   And closed channels are 'read' immediately. So, `with-channel`...

;; Primitives/Fundamentals:
;;  Go block
;;  Data channel (one direction)
;;  Signal channel (one direction)
;;  Alt/Alts/Select
;;  Take & Put
;;  Blocking/Parking


;; Note size of channel
(defn read-and-write []
  (let [my-pipe (async/chan 1)]
    (async/>!! my-pipe "Hi")
    (println "Read: " (async/<!! my-pipe))))

;; Note 'Read: ' is printing on a separate line from 'Hi'
(defn go-read-and-write []
  (let [my-pipe (async/chan)]
    (async/go (println "Read: " (async/<! my-pipe)))
    (async/>!! my-pipe "Hi")))

(defn lose-control-read-and-write []
  (let [my-pipe (async/chan)]
    (async/go (println "Read: " (async/<! my-pipe)))
    (async/go (async/>! my-pipe "Hi"))))

;; Note that many wonderful people can
;; write to and read from the same pipe!
(defn write-and-write-and-read []
  (let [my-pipe (async/chan 1)]
    (async/go (async/>! my-pipe "Write 1"))
    (async/go (async/>! my-pipe "Write 2"))
    (async/go (println "Read: " (async/<! my-pipe)))
    (println "Read again: " (async/<!! my-pipe))))

;; My pipe is a FIFO!
(defn which-end-do-i-use []
  (let [my-pipe (async/chan 5)]
    (async/go (async/>! my-pipe "Hi 1!")
              (let [msg (async/<! my-pipe)]
                (println "Read1 " msg)))
    (async/go (async/>! my-pipe "Hi 2!")
              (let [msg (async/<! my-pipe)]
                (println "Read2 " msg)))
    (async/go (async/>! my-pipe "Hi 3!")
              (let [msg (async/<! my-pipe)]
                (println "Read3 " msg)))
    (async/go (async/>! my-pipe "Hi 4!")
              (let [msg (async/<! my-pipe)]
                (println "Read4 " msg)))
    (async/>!! my-pipe "Hi 5!")))

;; But, I want to know when we're done!
(defn time-to-quit []
  (let [my-pipe (async/chan)
        quit
        (async/go (println "Read: " (async/<! my-pipe)))]
    (async/go (async/>! my-pipe "Hi"))
    (async/<!! quit)
    (println "I think we're all done!")))

;; Now everybody knows that it's quittin' time!
(defn quittin-time []
  (let [quit (async/chan)]
    (doseq [i (range 10)]
      (async/go (async/<! quit) (println (str "Worker " i " says:\"" "It's quittin' time!\""))))
    (async/go (async/<! (async/timeout 2000)) (async/close! quit))))

(defn kill-the-buddha-upon-the-road []
  (let [my-pipe (async/chan)
        quit (async/chan)]
    (async/go
      (println "Read: " (async/<! my-pipe))
      (async/<! quit)
      (println "Worker Quitting!"))

    (async/>!! my-pipe "Hi!")
    (async/<!! (async/timeout 2000))
    (async/close! quit)))

(defn waiting-for-lotsa-workers []
  (let [num-workers 100
        my-pipe (async/chan num-workers)
        quits (async/chan num-workers)]
    (doseq [i (range num-workers)]
      (async/go
        (async/>! my-pipe (str "Worker " i " reporting for duty!"))
        (async/<! (async/timeout (rand-int 2000)))
        (println (str "OK, " i " is done."))
        (async/>! quits i)))

    (loop [num-quitters 0]
      (when (< num-quitters num-workers)
        (async/alt!!
          my-pipe ([v c] (println v) (recur num-quitters))
          quits ([v c] (recur (inc num-quitters))))))
    (println "All done!")))

(defn its-getting-so-meta-in-here []
  (let [chans (async/chan)]
    (doseq [i (range 10)] (let [my-chan (async/chan)]
                            (async/go-loop []
                              (when (async/>! chans my-chan)
                                (let [work (async/<! my-chan)]
                                  (print (str "Worker " i ": doing work: " work "\n"))
                                  (when (not= work "quit")
                                    (recur)))))))
    (doseq [i (range 100)]
      (let [c (async/<!! chans)]
        (async/go
          (async/>! c (str "--work-" i)))))
    (async/close! chans)))


;; Sometimes, we need to close our channels!
(defmacro with-channels [bindings & body]
  (conj (concat body (for [p (reverse bindings)] (list 'async/close! p))) 'do))

;; OK, now for a big example
(defn shop [num-barbers customers-per-sec num-waiting-room-chairs total-customers]
  (let [quit (async/chan)
        queue (async/chan (async/dropping-buffer num-waiting-room-chairs))]
    (async/go-loop [times total-customers]
      (if (pos? times)
        (do
          (async/>! queue (str "customer " (- total-customers times)))
          (let [sleepy-times (quot 1000 customers-per-sec)]
            (async/<! (async/timeout sleepy-times)))
          (recur (dec times)))
        (async/close! quit)))

    (doseq [id (range num-barbers)]
      (async/go-loop []
        (async/alt!

          queue ([value channel]
                  (let [how-long (rand-int 2000)]
                    (println "Barber " id " on thread " (.getId (Thread/currentThread)))
                    #_(println "Barber " id " is barbing " value " for " how-long "msec.")
                    (async/<! (async/timeout how-long))
                    #_(println "Barber " id " finished barbing " value "."))
                  (recur)))))

    (async/<!! quit)
    (println "We seem to be all done...")))
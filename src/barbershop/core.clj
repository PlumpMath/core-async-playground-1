(ns barbershop.core
  (:require [clojure.core.async :as async]))

;; Primitives/Fundamentals:
;;  Go block
;;  Data channel (one direction)
;;  Signal channel (one direction)
;;  Alt/Alts/Select
;;  Take & Put
;;  Blocking/Parking



(defn write-and-read-channel-of-size-1 []
  (let [my-pipe (async/chan 1)]
    (async/>!! my-pipe "Hi")
    (println "Read: " (async/<!! my-pipe))))


;; You can write just about anything to a channel. Except nil. None of that.
;; Because nil represents reading from a closed channel.
;; Or the result of a go block which evaluated to nil...

(defn try-to-write-to-zero-sized-buffer-and-wait-forever []
  (let [my-pipe (async/chan)]
    (async/>!! my-pipe "Hi") ;; Uh oh...
    (println "Read: " (async/<!! my-pipe))))



(defn write-and-read-with-specified-buffer-size []
  (let [my-pipe (async/chan (async/buffer 5))]
    (async/>!! my-pipe "One")
    (async/>!! my-pipe "Two")
    (async/>!! my-pipe "Three")
    (async/>!! my-pipe "Four")
    (async/>!! my-pipe "Five")
    (async/>!! my-pipe "More")
    
    (println "Read: " (async/<!! my-pipe))))



;; It's easy to find bugs when your channel's buffer size is 0.
;; And they're easier to reason about.
;; No matter what buffer size you have, you'll run out eventually.
;; When you have to add some wiggle room, you've got a defect.





(defn try-to-read-and-wait-forever []
  (let [my-pipe (async/chan 1)]
    (println "Read: " (async/<!! my-pipe)) ;; Blocking...
    (async/>!! my-pipe "Hi")))





(defn go-read-and-write []
  (let [my-pipe (async/chan)]
    (async/go (println "Read: " (async/<! my-pipe)))
    (async/>!! my-pipe "Hi")))




;; Note that within a go block, reading is <! and writing is >!. Outside, it's <!! and >!!.
;; You'll notice the same with alt!! and alts!!. 




(defn go-blocks-have-complicated-relationships-with-threads []
  (async/<!!
   (async/go
     (print (str "before <!, thread id =" (.getName (java.lang.Thread/currentThread)) "\n"))
     (async/<! (async/timeout 100))
     (print (str "after <!, thread id =" (.getName (java.lang.Thread/currentThread)) "\n")))))




(defn which-end-do-i-use []
  (let [my-pipe (async/chan 5)]
    (async/go (async/>! my-pipe "Hi 1!")
              (let [msg (async/<! my-pipe)]
                (print (str "Read1 " msg "\n"))))
    (async/go (async/>! my-pipe "Hi 2!")
              (let [msg (async/<! my-pipe)]
                (print (str "Read2 " msg "\n"))))
    (async/go (async/>! my-pipe "Hi 3!")
              (let [msg (async/<! my-pipe)]
                (print (str "Read3 " msg "\n"))))
    (async/go (async/>! my-pipe "Hi 4!")
              (let [msg (async/<! my-pipe)]
                (print (str "Read4 " msg "\n"))))
    (async/>!! my-pipe "Hi 5!")))


;;
;; func a() {
;;   c := make(chan int)
;;   go func(c1 <-chan int) {
;;      fmt.Printf("%d\n", <-c1)
;;   }(c)
;;   go func(c1 chan<-) {
;;      c1 <- 5
;;   }
;; }
;;

;; Always use a channel for 1 purpose in a given thread/context.
;; In Go, they're usually typed as read-only or write-only.
;; This also means that workers shouldn't have a state which is
;; modified by another thread!!




(defn leverage-blocking-with-timeouts []
  (async/<!! (async/timeout 10000))
  (println "All done waiting!"))




(defn reading-from-one-of-several-channels []
  (let [chans [(async/chan) (async/chan)]
        which-one (rand-int 2)
        chan-to-use (chans which-one)]
    (async/go
      (async/>! chan-to-use (str "<Channel:" which-one ">")))


    (let [[val chan] (async/alts!! chans)]
      (println "Got value " val))))




(defn alt-is-better-for-distinct-meaningful-channels []
  (let [chan1 (async/chan)
        chan2 (async/chan)
        chans [chan1 chan2]
        which-one (rand-int 2)
        chan-to-use (chans which-one)]
    (async/go
      (async/>! chan-to-use (str "<Channel:" which-one ">")))


    (async/alt!!
      chan1 ([val chan] (println "Heard from first channel"))
      chan2 ([val chan] (println "Heard from second channel")))))




(defn writing-to-one-of-several-channels []
  (let [chans [(async/chan) (async/chan)]
        which-one (rand-int 2)
        msg (str "<Channel:" which-one ">")
        chan-to-use (chans which-one)]
    (async/go
      (println "Got value " (async/<!! chan-to-use)))


    ;; Use the channel that's ready
    ;; async/alts!! [[write-chan1 val1]
    ;;               [write-chan2 val2]
    ;;               read-chan1
    ;;               read-chan2
    ;;              ]

    (async/alts!! (mapv (juxt identity (constantly msg)) chans))))



(defn maybe-we-can-choose-a-reader-and-a-writer []
  (let [chan1 (async/chan)
        chan2 (async/chan)]
    (async/go ;; a worker thread which reads from chan1
      (async/<! (async/timeout (rand-int 5000)))
      (println "Reading worker saw value: " (async/<! chan1)))
    (async/go ;; a worker thread which writes to chan2
      (async/<! (async/timeout (rand-int 5000)))
      (async/>! chan2 "2.718281828459045"))
    (dotimes [_ 2]
      (async/alt!!
        chan2 ([val ch] (println "Got value " val " from writing worker"))
        [[chan1 "3.14159268"]] :value-returned-from-alt))))

;; Hmmm; value-returned-from-alt is returned regardless of whether chan1 is closed...
;; That sounds bad...



;; Note that many wonderful people can
;; write to and read from the same pipe!
(defn write-and-write-and-read-and-read []
  (let [my-pipe (async/chan 1)]
    (async/go (async/>! my-pipe "Write 1"))
    (async/go (async/>! my-pipe "Write 2"))
    (async/go (print (str "Read: " (async/<! my-pipe) "\n")))
    (async/go (print (str "Read again: " (async/<!! my-pipe) "\n"))))
  "let's wait, shall we?")



;; Channels should be used to communicate one purpose. Don't overload them.
;; Data should generally not be split into multiple messages.



(defn lose-control-read-and-write []
  (let [my-pipe (async/chan)]
    (async/go (println "Read: " (async/<! my-pipe)))
    (async/go (async/>! my-pipe "Hi")))
  "all done")





(defn when-did-they-quit? []
  (let [my-pipe (async/chan)
        quit (async/go (println "Read: " (async/<! my-pipe)))]
    (async/go (async/>! my-pipe "Hi"))
    (async/<!! quit)
    (println "I think we're all done!")))




(defn waiting-for-lotsa-workers []
  (let [num-workers 10
        quits (set (map #(async/go
                           (async/<! (async/timeout (rand-int 2000)))
                           (print (str "OK, " % " is done.\n")))
                        (range num-workers)))]

    (loop [quits quits]
      (when-not (empty? quits)
        (let [[val chan] (async/alts!! (vec quits))]
          (print (str "Got value: <" (nil?  val) ">"))
          (recur (disj quits chan)))))
    (println "All done!")))



;; These loops and alts tend to form tight knots of logic.
;; It's up to you to keep them as simple as possible.




(defn hey-everyone-its-quittin-time []
  (let [quit (async/chan)]
    (dotimes [i 10]
      (async/go
        (async/alt!
          quit ([val chan] (print (str "Worker " i " says: \"It's quittin' time!\"\n"))))))
    (async/<!! (async/timeout 2000))
    (async/close! quit)))




(defn kill-the-buddha-upon-the-road []
  (let [quit (async/chan)]
    (dotimes [i 10]
      (async/go
        (async/<! quit)
        (print (str "Worker " i " is quitting!\n"))))

    (async/<!! (async/timeout 2000))
    (async/close! quit)))




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




;; You can select who to write to as well as who to read from!
(defn whos-ready-for-some-data []
  (let [chans [(async/chan) (async/chan)]]
    (doseq [i (range (count chans))]
      (let [c (chans i)]
        (async/go-loop []
          (when-let [data (async/<! c)]
            (println (str "Reading " data " in worker " i))
            (async/<! (async/timeout 1000))
            (recur)))))
    (doseq [s ["one" "two" "three" "four"]]
      (async/alts!! (mapv (juxt identity (constantly s)) chans)))
    (async/close! (chans 1))
    (async/close! (chans 2))))




;; Sometimes, we need to close our channels!
(defmacro with-channels [bindings & body]
  (concat (list 'let) (vector bindings) body (for [p (reverse (map first (partition 2 bindings)))] (list 'async/close! p))))




;; Testing is challenging because you don't want a thread to sleep indefinitely.
;;   But, there's a trick; deadlocks OFTEN come from threads waiting to read
;;   from a channel.
;;   And closed channels are 'read' immediately. So, `with-channel`...




(defn just-a-little-deadlock-nothin-to-see-here []
  (with-channels [a (async/chan)]
    (doseq [i (range 5)]
      (async/go
        (println (str "Worker " i " waiting for data..."))
        (let [data (async/<! a)]
          (println (str "Worker " i " read " data)))))
    (async/<!! (async/timeout 6000))
    (println "Giving up...")))




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

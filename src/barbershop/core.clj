(ns barbershop.core
  (:require [clojure.core.async :as async]))

;; Primitives/Fundamentals:
;;  Channels
;;     Data channel
;;     Signal channel
;;  Take & Put
;;  Go block
;;  Alt/Alts
;;  Blocking/Parking
;;  Channels -> seq functions
;;  Patterns functions





(defn write-and-read-channel-of-size-1 []
  (let [my-pipe (async/chan 1)
        write-result (async/>!! my-pipe "Hi")
        read-result (async/<!! my-pipe)]
    (println "Write: " write-result)
    (println "Read: " read-result)))




;; You can write just about anything to a channel. Except nil. None of that.
;; Because nil represents reading from a closed channel.
;; Or the result of a go block which evaluated to nil...




(defn try-to-write-to-zero-sized-buffer-and-wait-forever []
  (let [my-pipe (async/chan)]
    (async/>!! my-pipe "Hi") ;; Uh oh...
    (println "Read: " (async/<!! my-pipe))))




(defn try-to-read-and-wait-forever []
  (let [my-pipe (async/chan)]
    (println "Read: " (async/<!! my-pipe)) ;; Blocking...
    (async/>!! my-pipe "Hi")))




(defn write-and-read-with-specified-buffer-size []
  (let [my-pipe (async/chan 5)]
    (async/>!! my-pipe "One")
    (async/>!! my-pipe "Two")
    (async/>!! my-pipe "Three")
    (async/>!! my-pipe "Four")
    (async/>!! my-pipe "Five")
    (async/>!! my-pipe "More")   ;; blocked!
    
    (println "Read: " (async/<!! my-pipe))))



;; It's easy to find bugs when your channel's buffer size is 0.
;; And they're easier to reason about.
;; No matter what buffer size you have, you'll run out eventually.
;; When you have to add some wiggle room, you've got a defect.





(defn you-can-specify-the-buffer-too []
  (let [buffered-pipe   (async/chan 5)
        same-pipe       (async/chan (async/buffer 5))
        always-writable (async/chan (async/dropping-buffer 5))
        also-writable   (async/chan (async/sliding-buffer 5))]
    (async/>!! always-writable "one")
    (async/>!! always-writable "two")
    (async/>!! always-writable "three")
    (async/>!! always-writable "four")
    (async/>!! always-writable "five")
    ;; None of the following block
    (async/>!! always-writable "six")
    (async/>!! always-writable "seven")
    (async/>!! always-writable "eight")
    (async/>!! always-writable "nine")
    (async/>!! always-writable "ten")
    (println "From dropping buffer: " (async/<!! always-writable))

    (async/>!! also-writable "one")
    (async/>!! also-writable "two")
    (async/>!! also-writable "three")
    (async/>!! also-writable "four")
    (async/>!! also-writable "five")
    ;; None of the following block
    (async/>!! also-writable "six")
    (async/>!! also-writable "seven")
    (async/>!! also-writable "eight")
    (async/>!! also-writable "nine")
    (async/>!! also-writable "ten")
    (println "From sliding buffer: " (async/<!! also-writable))))




(defn what-happens-when-the-channels-closed []
  (let [my-pipe (async/chan 1)]
    (println "Before close, write returns: " (async/>!! my-pipe "Hi"))
    (async/close! my-pipe)
    (println "After close, write return: " (async/>!! my-pipe "There"))
    (println "First read on closed pipe returns: " (async/<!! my-pipe))
    (println "Second read on closed pipe returns: " (async/<!! my-pipe))
    (println "And further reads return: " (async/<!! my-pipe))))




(defn go-read-and-write []
  (let [my-pipe (async/chan)]
    (async/go (println "Read: " (async/<! my-pipe)))
    (async/go (async/>! my-pipe "Hi"))
    (println "Hi there!!")))




;; Note that within a go block, reading is <! and writing is >!. Outside, it's <!! and >!!.
;; You'll notice the same with alt!! and alts!!. 



(defn go-blocks-have-return-values []
  (let [go-rtn (async/go
                 (println "Inside a go block!")
                 (+ 1 2))]
    (println "Go returned: " (async/<!! go-rtn))))




(defn go-blocks-have-complicated-relationships-with-threads []
  (async/<!!
   (async/go
     (print (str "before <!!, thread id =" (.getName (java.lang.Thread/currentThread)) "\n"))
     (async/<!! (async/timeout 100))
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
;; type WorkMessage struct { ... }
;;
;; func printIt(c <-chan WorkMessage) {
;;   fmt.Printf("%v\n", <-c)
;; }
;; func writeIt(c chan<- WorkMessage) {
;;   c <- WorkMessage{ ... }
;; }
;; func a() {
;;   c := make(chan WorkMessage)
;;   go printIt(c)
;;   go writeIt(c)
;; }
;;

;; Always use a channel for 1 purpose in a given thread/context.
;; In Go, they're usually typed as read-only or write-only.

;; Messages should be self-contained, and not split into multiple puts.

;; This also means that workers shouldn't have a state which is
;; modified by another thread!!




(defn leverage-blocking-with-timeouts []
  (async/<!! (async/timeout 10000))
  (println "All done waiting!"))





;; 'port' is a superset of 'channel'.



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


(defn hey-everyone-its-quittin-time--hello? []
  (let [quit (async/chan)]
    (dotimes [i 10]
      (async/go
        (async/alt!
          quit ([val chan] (print (str "Worker " i " says: \"It's quittin' time!\"\n"))))))
    (async/<!! (async/timeout 2000))
    (dotimes [i 10] (async/>!! quit :quit))))


(defn kill-the-buddha-upon-the-road []
  (let [quit (async/chan)]
    (dotimes [i 10]
      (async/go
        (async/alt!
          quit ([val chan] (print (str "Worker " i " says: \"It's quittin' time!\"\n"))))))
    (async/<!! (async/timeout 2000))
    (async/close! quit)))






(defn its-getting-so-meta-in-here []
  (let [chans (async/chan)]
    (dotimes [i 10]
      (let [my-chan (async/chan)]
        (async/go-loop []
          (when (async/>! chans my-chan)
            (let [work (async/<! my-chan)]
              (print (str "Worker " i ": doing work: " work "\n"))
              (when (not= work "quit")
                (recur)))))))
    (dotimes [i 20]
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
  `(let ~bindings
     (async/go ~@body)
     (async/<!! (async/timeout 3000))
     ~(list #_ (map ?? (reverse (map first (partition 2 bindings))))))
   (concat (list 'let) (vector bindings) body (for [p (reverse (map first (partition 2 bindings)))] (list 'async/close! p)))
  )

;; show an expansion of the macro...
;; clean up macro


;; Testing is challenging because you don't want a thread to sleep indefinitely.
;;   But, there's a trick; deadlocks OFTEN come from threads waiting to read
;;   from a channel.
;;   And closed channels are 'read' immediately. So, `with-channel`...


;; show a real-world example around alt/alts - persister?


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

    (dotimes [id num-barbers]
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

(ns server.main
  (:require ["fs" :as fs]
            ["path" :as path]
            [clojure.string :as str]
            ["midi" :as midi]))

(defonce piano-midi-input-name "Yamaha Portable G-1")
;; (def recording-output-folder "")

(def initial-state {:input nil
                    :read-stream nil
                    :write-stream nil
                    :interval nil})

(def state (atom {:input nil
                  :read-stream nil
                  :write-stream nil
                  :interval nil}))

; TODO: I feel like there's a cleaner way of "handling" this sort of thing - not the greatest code... Very stateful, etc etc

; Steps
; figure out how to effectively lcose the streams? i think u close readstream which triggers the write stream close cb?? who knows... and how do u make sure all the data was transferred. unpipe()?? etc etc...
; 1 pipe createReadStream to createWriteStream to a .midi file for the MDII
; 2 get the audio too
; ensure everythng works when input dies
; 3 make sure ur creating a new file when input resets
; set your script to run on startup Windows (figure out a simple system for you to add scripts, like you have ur tools folder on desktop and u want that to be kinda portable i think; prob want scritp[s to be portable too? uhh... yeah probably, cuz manual isntall is tedious])

; TODO use createReadStream I think (handles writes for you?)
; TODO: Should you write to disk every time new info comes in? prob not... something something buffers and batch writes.
; soo then, yeah just set an interval to write to disk, and force write to disk when input dies, b4 script close, etc...? well what if my PC shuts off? I mean... this isn't bank data

(defn- start-piano-recording! [[input port]]
  (println [port input])
  (let [midi-filename (-> (str (-> (js/Date.) (. toISOString)) ".midi")
                          (str/replace ":" "_"))
        midi-filepath (path/resolve js/__dirname midi-filename)
        _ (fs/writeFileSync midi-filepath, "") ; TODO do i need this anymore? issue may have just been the filename
        midi-stream (midi/createReadStream input)
        write-stream (fs/createWriteStream midi-filepath)]
    (swap! state assoc :input input :write-stream write-stream :read-stream midi-stream)
    (. input openPort port)
    (-> midi-stream
        (.pipe write-stream))
    (. write-stream on "close" #(println (str "Finished writing '" midi-filepath "'")))))

; TODO I don't call closePort on the now dead input... doesn't seem to cause issues? Presumably the port auto-closes if the USB device disconnects?...
(defn- reset-state! []
  (println "Resetting state...")
  (let [{:keys [interval]} @state]
    (when interval (js/clearInterval interval))
    ;; (when write-stream (.end write-stream))
    ;; (when read-stream (.close read-stream))
    (reset! state initial-state)))

(defn- check-for-midi-input! []
  (let [input (midi/Input.)
        input-count (. input getPortCount)
        inputs (for [port (range 0 input-count)]
                 [port (. input getPortName port)])
        my-piano-input (first (filter #(= (last %) piano-midi-input-name) inputs))]
    (when (and my-piano-input (not (:input @state))) (start-piano-recording! [input (first my-piano-input)]))
    (when (and (:input @state) (not my-piano-input)) (reset-state!))))

(defn- listen-for-midi-input! []
  (swap! state assoc :interval (js/setInterval check-for-midi-input! 5000)))

#_:clj-kondo/ignore
(defn ^:dev/before-load stop []
  (js/console.log "Reloading...")
  (reset-state!))

#_:clj-kondo/ignore
(defn ^:dev/after-load reload! []
  (listen-for-midi-input!)
  (println "Code updated."))

#_:clj-kondo/ignore
(defn main! []
  (listen-for-midi-input!)
  (println "App loaded!"))

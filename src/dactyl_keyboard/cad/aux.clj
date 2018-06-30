;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Auxiliary Features                                                  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.cad.aux
  (:require [scad-clj.model :exclude [use import] :refer :all]
            [unicode-math.core :refer :all]
            [dactyl-keyboard.generics :as generics]
            [dactyl-keyboard.params :refer :all]
            [dactyl-keyboard.cad.misc :refer :all]
            [dactyl-keyboard.cad.matrix :refer :all]
            [dactyl-keyboard.cad.key :refer :all]
            [dactyl-keyboard.cad.body :refer :all]))


;;;;;;;;;;;;;;;;;;;;;
;; Microcontroller ;;
;;;;;;;;;;;;;;;;;;;;;

;; MicroUSB female:
(def micro-usb-width 7.5)
(def micro-usb-length 5.3)
(def micro-usb-height 2.8)

(def micro-usb-receptacle
  "A USB female."
  (color [0.5 0.5 0.5 1]
    (cube micro-usb-width micro-usb-length micro-usb-height)))
(def micro-usb-channel (cube 7.8 10 micro-usb-height))

;; Teensy MCU: Not fully supported at the moment. Pro Micro is hardcoded below.
(def teensy-width 20)
(def teensy-height 12)
(def teensy-length 33)
(def teensy2-length 53)

;; Arduino Pro Micro MCU:
(def promicro-width 18)
(def promicro-length 33)
(def promicro-thickness 1.65)

(def mcu-thickness-tolerance 0.3)

(def mcu-microusb-offset
  "A millimetre offset between an MCU PCB and a micro-USB female."
  [0
   (+ (/ promicro-length 2) 1 (/ micro-usb-length -2))
   (+ (/ promicro-thickness 2) (/ micro-usb-height 2))])

(def promicro-pcb (cube promicro-width promicro-length promicro-thickness))
(def mcu-model
  (union
    (translate mcu-microusb-offset micro-usb-receptacle)
    (color [26/255, 90/255, 160/255 1] promicro-pcb)))

(def mcu-space-requirements
  "Negative space for an MCU in use, including USB connectors."
  (let [alcove-width (+ micro-usb-height promicro-thickness mcu-thickness-tolerance 2)
        alcove-height (+ promicro-width 1)]
    (union
      (translate mcu-microusb-offset
        (union
          ;; Female USB connector:
          micro-usb-channel
          ;; Male USB connector (enable in case of obstruction):
          #_(hull
             (translate [0 4 0] (cube 15 1 10))
             (translate [0 9 0] (cube 17 1 12)))))
      ;; An alcove in the inner wall, because a blind notch is hard to clean:
      (translate [0
                  (/ (- promicro-length alcove-height) 2)
                  (- (/ alcove-width 2) promicro-thickness)]
        (cube (+ promicro-width 5) alcove-height alcove-width))
      ;; The negative of the PCB to put a notch in the spine:
      (cube promicro-width promicro-length (+ promicro-thickness mcu-thickness-tolerance))
      ;; Extra space for wiring running very near the MCU:
      (translate [(/ promicro-width -2) 0 0]
        (rotate [(+ (/ π 2) (/ π 14)) 0 (/ π -18)]
          (cylinder 4 (- promicro-length 10)))))))

(defn mcu-finger-coordinates [getopt]
  (let [by-col (getopt :key-clusters :finger :derived :coordinates-by-column)]
    (last (by-col mcu-finger-column))))

(defn mcu-position [getopt shape]
  "Transform passed shape into the reference frame for an MCU holder."
  (let [[x y] (take 2
                (cluster-position
                  getopt
                  :finger
                  (mcu-finger-coordinates getopt)
                  (wall-slab-center-offset getopt :finger (mcu-finger-coordinates getopt) mcu-connector-direction)))]
   (->>
     shape
     ;; Put the USB end of the PCB at [0, 0, 0].
     (translate [0 (/ promicro-length -2) 0])
     ;; Flip it to stand on the long edge for soldering access.
     ;; Have the components and silk face the interior of the housing.
     (rotate (/ π 2) [0 1 0])
     ;; Have the components and silk face the interior of the housing.
     (translate [0 0 (/ promicro-width 2)])
     ;; Turn it around the z axis to point USB in the ordered direction.
     (rotate (- (compass-radians mcu-connector-direction)) [0 0 1])
     ;; Move it to the ordered case wall.
     (translate [x y 0])
     ;; Tweak as ordered.
     (translate mcu-offset))))

(defn mcu-visualization [getopt] (mcu-position getopt mcu-model))
(defn mcu-negative [getopt] (mcu-position getopt mcu-space-requirements))

;; Holder for MCU:
(defn mcu-support [getopt]
  (let [plinth-width (+ promicro-thickness mcu-thickness-tolerance 1.2)
        plinth-height (nth mcu-offset 2)
        grip-depth 0.6
        grip-to-base 5
        rev-dir (turning-left (turning-left mcu-connector-direction))
        cervix-coordinates (walk-matrix (mcu-finger-coordinates getopt) rev-dir rev-dir)]
    (union
      (mcu-position getopt
        (union
          ;; A little gripper to stabilize the PCB horizontally.
          ;; This is intended to be just shallow enough that the outer wall
          ;; will bend back far enough for the installation and is placed to
          ;; avoid covering any of the through-holes.
          (translate
            [0 (+ (/ promicro-length -2) (/ grip-depth 2)) 0]
            (cube (/ promicro-width 2) grip-depth plinth-width))
          ;; A block to support the gripper.
          (translate
            [0 (- (/ promicro-length -2) (/ grip-to-base 2)) 0]
            (cube (/ promicro-width 2) grip-to-base plinth-width))))
      ;; The spine connects a sacrum, which is the main body of the plinth
      ;; at ground level, with a cervix that helps support the finger web.
      (hull
        (mcu-position getopt
          (translate
            [0 (- (/ promicro-length -2) grip-to-base) 0]
            (cube (/ promicro-width 2) grip-to-base plinth-width)))
        (cluster-place getopt :finger cervix-coordinates
          (mount-corner-post getopt [mcu-connector-direction (turning-left rev-dir)]))
        (cluster-place getopt :finger cervix-coordinates
          (mount-corner-post getopt [mcu-connector-direction (turning-right rev-dir)]))))))


;;;;;;;;;;;;;;;;
;; Back Plate ;;
;;;;;;;;;;;;;;;;

;; Plate for a connecting beam, rod etc.

(defn backplate-place [getopt shape]
  (let [position (getopt :case :back-plate :position)
        {alias :key-alias offset :offset} position
        keyinfo (getopt :key-clusters :derived :aliases alias)
        {cluster :cluster coordinates :coordinates} keyinfo]
   (->>
     shape
     (translate (cluster-position getopt cluster coordinates
                  (wall-slab-center-offset getopt cluster coordinates :north)))
     (translate [0 0 (/ (getopt :case :back-plate :beam-height) -2)])
     (translate offset))))

(defn backplate-shape [getopt]
  "A mounting plate for a connecting beam."
  (let [height (getopt :case :back-plate :beam-height)
        width (+ (getopt :case :back-plate :fasteners :distance) height)
        depth 3
        interior-protrusion 8
        exterior-bevel 1
        interior-bevel 7]
   (hull
     (translate [0 (- interior-protrusion) 0]
       (cube (- width interior-bevel) depth (- height interior-bevel)))
     (cube width depth height)
     (translate [0 exterior-bevel 0]
       (cube (dec width) depth (dec height))))))

(defn backplate-fastener-holes [getopt]
  "Two holes for screws through the back plate."
  (let [d (getopt :case :back-plate :fasteners :diameter)
        D (getopt :case :back-plate :fasteners :distance)
        hole (fn [x-offset]
               (->>
                 (union
                   (cylinder (/ d 2) 25)
                   (if (getopt :case :back-plate :fasteners :bosses)
                     (translate [0 0 10]
                       (iso-hex-nut-model d 10))))
                 (rotate [(/ π 2) 0 0])
                 (translate [x-offset 0 0])
                 (backplate-place getopt)))]
   (union
     (hole (/ D 2))
     (hole (/ D -2)))))

(defn backplate-block [getopt]
  (bottom-hull (backplate-place getopt (backplate-shape getopt))))


;;;;;;;;;;;;;;;
;; LED Strip ;;
;;;;;;;;;;;;;;;

(defn west-wall-west-points [getopt]
  (for [row ((getopt :key-clusters :finger :derived :row-indices-by-column) 0)
        corner [generics/WSW generics/WNW]]
   (let [[x y _] (wall-corner-position getopt :finger [0 row] corner)]
    [(+ x (getopt :by-key :parameters :wall :thickness)) y])))

(defn west-wall-east-points [getopt]
  (map (fn [[x y]] [(+ x 10) y]) (west-wall-west-points getopt)))

(defn west-wall-led-channel [getopt]
  (let [west-points (west-wall-west-points getopt)
        east-points (west-wall-east-points getopt)]
    (extrude-linear {:height 50}
      (polygon (concat west-points (reverse east-points))))))

(defn led-hole-position [getopt ordinal]
  (let [by-col (getopt :key-clusters :finger :derived :row-indices-by-column)
        row (first (by-col 0))
        [x0 y0 _] (wall-corner-position getopt :finger [0 row] generics/WNW)
        h (+ 5 (/ (getopt :case :leds :housing-size) 2))]
   [x0 (+ y0 (* (getopt :case :leds :interval) ordinal)) h]))

(defn led-emitter-channel [getopt ordinal]
  (->> (cylinder (/ (getopt :case :leds :emitter-diameter) 2) 50)
       (rotatev (/ π 2) [0 1 0])
       (translate (led-hole-position getopt ordinal))))

(defn led-housing-channel [getopt ordinal]
  (let [h (getopt :case :leds :housing-size)]
   (->> (cube 50 h h)
        (translate (led-hole-position getopt ordinal)))))

(defn led-holes [getopt]
  (let [holes (range (getopt :case :leds :amount))
        housings (apply union (map (partial led-housing-channel getopt) holes))
        emitters (apply union (map (partial led-emitter-channel getopt) holes))]
   (union
     (intersection
       (west-wall-led-channel getopt)
       housings)
     emitters)))


;;;;;;;;;;;;;;;;
;; Signalling ;;
;;;;;;;;;;;;;;;;


(defn connection-nook [getopt]
  "Produce coordinates for translation into requested corner."
  (let [socket-size (getopt :connection :socket-size)
        thickness (getopt :case :web-thickness)
        corner (getopt :connection :position :corner)
        use-housing (and (getopt :case :rear-housing :include)
                         (getopt :connection :position :prefer-rear-housing))
        general
          (if use-housing
            (housing-position getopt corner 3 [0 0 0])
            (let [alias (getopt :connection :position :key-alias)
                  keyinfo (getopt :key-clusters :derived :aliases alias)
                  {cluster :cluster coordinates :coordinates} keyinfo]
             (wall-corner-position getopt cluster coordinates corner)))
        to-nook
          (if use-housing
            (let [{dxs :dx dys :dy} (compass-to-grid (second corner))
                  distance (* 0.5 (+ thickness (first socket-size)))]
             [(* -1 dxs distance) (* -1 dys distance) 0])
            ;; Else don’t bother.
            [0 0 0])]
   (vec (map + (conj (vec (take 2 general)) 0) to-nook))))

(defn connection-position [getopt shape]
  (let [socket-size (getopt :connection :socket-size)
        thickness (getopt :case :web-thickness)
        corner (getopt :connection :position :corner)]
   (->> shape
        (rotate (getopt :connection :position :rotation))
        ;; Line up with the (rear housing) wall and a metasocket base plate.
        (translate [0 (/ thickness 2) thickness])
        ;; Imagine the outside of the wall as the x axis line, ground level
        ;; as the y axis line.
        (translate (vec (map * [0 -0.5 0.5] socket-size)))
        ;; Rotate around that origin to face the corner’s main direction.
        (rotate [0 0 (- (compass-radians (first corner)))])
        ;; Bring snugly to the requested corner.
        (translate (connection-nook getopt))
        ;; Tweak.
        (translate (getopt :connection :position :offset)))))

(defn connection-metasocket [getopt]
  "The shape of a socket in the case to receive a signalling socket component.
  Here, the shape nominally faces north."
  (let [socket-size (getopt :connection :socket-size)
        thickness (getopt :case :web-thickness)
        double (* thickness 2)]
   (translate [0 (/ thickness -2) 0]
     (apply cube (vec (map + socket-size [double thickness double]))))))

(defn connection-socket [getopt]
  "Negative space for a port, with a hole for wires leading out of the port and
  into the interior of the keyboard."
  (let [socket-size (getopt :connection :socket-size)
        thickness (getopt :case :web-thickness)]
   (union
     (apply cube socket-size)
     (translate [0 (* -2 thickness) 0]
       (apply cube (map dec socket-size))))))

(defn connection-positive [getopt]
  (connection-position getopt (connection-metasocket getopt)))

(defn connection-negative [getopt]
  (connection-position getopt (connection-socket getopt)))


;;;;;;;;;;;;;;;;;;;;
;; Minor Features ;;
;;;;;;;;;;;;;;;;;;;;

(defn foot-plates [getopt]
  "Model plates from polygons.
  Each vector specifying a point in a polygon must have a key and a mount
  corner identified by a direction tuple. These can be followed by
  a two-dimensional offset for tweaking."
   (letfn [(point [{key-alias :key-alias directions :key-corner offset :offset
                    :or {offset [0 0]}}]
             (let [key (getopt :key-clusters :derived :aliases key-alias)
                   {cluster :cluster coordinates :coordinates} key
                   base (take 2 (wall-corner-position
                                  getopt cluster coordinates directions))]
               (vec (map + base offset))))
           (plate [polygon-spec]
             (extrude-linear
               {:height (getopt :case :foot-plates :height) :center false}
               (polygon (map point (:points polygon-spec)))))]
    (apply union (map plate (getopt :case :foot-plates :polygons)))))

;; USB female holder:
;; This is not needed if the MCU has a robust integrated USB connector and that
;; connector is directly exposed through the case.
(def usb-holder-size [6.5 10.0 13.6])
(def usb-holder-thickness 4)

(defn usb-holder-placement [getopt shape]
  (let [coordinates [0 0]
        origin
          (cluster-position
            getopt
            :finger
            coordinates
            [0 (/ mount-depth 2) 0])]
    (translate [(first origin)
                (second origin)
                (/ (+ (last usb-holder-size) usb-holder-thickness) 2)]
               shape)))

(defn usb-holder-positive [getopt]
  (usb-holder-placement
    getopt
    (cube (+ (first usb-holder-size) usb-holder-thickness)
          (second usb-holder-size)
          (+ (last usb-holder-size) usb-holder-thickness))))

(defn usb-holder-negative [getopt]
  (usb-holder-placement getopt (apply cube usb-holder-size)))

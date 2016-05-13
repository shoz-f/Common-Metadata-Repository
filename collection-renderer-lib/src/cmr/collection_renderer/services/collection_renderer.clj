(ns cmr.collection-renderer.services.collection-renderer
  "Defines a component which can be used to generate an HTML response of a UMM-C collection. Uses the
   MMT ERB code along with JRuby to generate it."
  (require [cmr.common.lifecycle :as l]
           [clojure.java.io :as io]
           [cmr.umm-spec.umm-json :as umm-json])
  (import [javax.script
           ScriptEngine
           ScriptEngineManager
           Invocable]
          [java.io
           ByteArrayInputStream]))

(def system-key
  "The key to use when storing the collection renderer"
  :collection-renderer)

(def bootstrap-rb
  "A ruby script that will bootstrap the JRuby environment to contain the appropriate functions for
   generating ERB code."
  (io/resource "collection_preview/bootstrap.rb"))

(def collection-preview-erb
  "The main ERB used to generate the Collection HTML."
  (io/resource "collection_preview/collection_preview.erb"))

(defn- create-jruby-runtime
  "Creates and initializes a JRuby runtime."
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    (.eval jruby (io/reader bootstrap-rb))
    jruby))

;; An wrapper component for the JRuby runtime
(defrecord CollectionRenderer
  [jruby-runtime]
  l/Lifecycle

  (start
    [this _system]
    (assoc this :jruby-runtime (create-jruby-runtime)))
  (stop
    [this _system]
    (dissoc this :jruby-runtime)))

(defn create-collection-renderer
  "Returns an instance of the collection renderer component."
  []
  (->CollectionRenderer nil))

(defn- render-erb
  "Renders the ERB resource with the given JRuby runtime, URL to an ERB on the classpath, and a map
   of arguments to pass the ERB."
  [jruby-runtime erb-resource args]
  (.invokeFunction
   ^Invocable jruby-runtime
   "java_render"
   (to-array [(io/input-stream erb-resource) args])))

(defn- context->jruby-runtime
  [context]
  (get-in context [:system system-key :jruby-runtime]))

(defn render-collection
  "Renders a UMM-C collection record and returns the HTML as a string."
  [context collection]
  (let [umm-json (umm-json/umm->json collection)]
   (render-erb (context->jruby-runtime context) collection-preview-erb {"umm_json" umm-json})))

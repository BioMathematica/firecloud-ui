(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
   ))


(react/defc MethodsList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count (:methods props)))
        (style/create-message-well "No methods to display.")
        [table/Table
         {:columns [{:header "Namespace" :starting-width 100}
                    {:header "Name" :starting-width 100}
                    {:header "Synopsis" :starting-width 300}]
          :data (map (fn [m]
                       [(m "namespace")
                        (m "name")
                        (m "synopsis")])
                  (:methods props))}])])})


(defn- contains-text [text fields]
  (fn [method]
    (some #(not= -1 (.indexOf (method %) text)) fields)))

(defn- filter-methods [methods fields text]
  (filter (contains-text text fields) methods))

(defn- create-mock-methods []
  (map
    (fn [i]
      {:namespace (rand-nth ["broad" "public" "nci"])
       :name (str "Method " (inc i))
       :synopsis (str "This is method " (inc i))})
    (range (rand-int 100))))


(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {:style {:padding "1em"}}
      [:h2 {} "Method Repository"]
      [:div {}
       (cond
         (:methods-loaded? @state) [:div {}
                                    (let [apply-filter #(swap! state assoc
                                                          :filter-text
                                                          (.-value (.getDOMNode (@refs "input"))))]
                                      [:div {:style {:paddingBottom "1em" :paddingLeft "4em"}}
                                       (style/create-text-field {:ref "input" :placeholder "Filter"
                                                                 :onKeyDown (common/create-key-handler
                                                                              [:enter] apply-filter)})
                                       [:span {:style {:paddingLeft "1em"}}]
                                       [comps/Button {:icon :search :onClick apply-filter}]])
                                    [MethodsList {:methods (filter-methods
                                                             (:methods @state)
                                                             ["namespace" "name" "synopsis"]
                                                             (or (:filter-text @state) ""))}]]
         (:error @state) (style/create-server-error-message (get-in @state [:error :message]))
         :else [comps/Spinner {:text "Loading methods..."}])]])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
       "/methods"
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (let [methods (utils/parse-json-string (.-responseText xhr))]
                       (swap! state assoc :methods-loaded? true :methods methods))
                     (swap! state assoc
                            :error {:message (.-statusText xhr)})))
        :canned-response {:responseText (utils/->json-string (create-mock-methods))
                          :status 200
                          :delay-ms (rand-int 2000)}}))})


(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(def machineTypes ["n1-standard-1"
                   "n1-standard-2"
                   "n1-standard-4"
                   "n1-standard-8"
                   "n1-standard-16"
                   "n1-standard-32"
                   "n1-standard-64"
                   "n1-standard-96"
                   "n1-highcpu-4"
                   "n1-highcpu-8"
                   "n1-highcpu-16"
                   "n1-highcpu-32"
                   "n1-highcpu-64"
                   "n1-highcpu-96"
                   "n1-highmem-2"
                   "n1-highmem-4"
                   "n1-highmem-8"
                   "n1-highmem-16"
                   "n1-highmem-32"
                   "n1-highmem-64"
                   "n1-highmem-96"])


(def spinner-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   (spinner)])

(defn icon-for-cluster-status [status]
  (case status
    ("Deleted" "Error") (moncommon/render-failure-icon)
    ("Creating" "Updating" "Deleting") spinner-icon
    "Running" (moncommon/render-success-icon)
    "Unknown" (moncommon/render-unknown-icon)))

(defn create-inline-form-label [text]
  [:span {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])

(defn- leo-notebook-url [cluster]
  (str (config/leonardo-url-root) "/notebooks/" (:googleProject cluster) "/" (:clusterName cluster)))

(defn- contains-statuses [clusters statuses]
  (seq (set/intersection (set statuses) (set (map :status clusters)))))

(react/defc- ClusterCreator
  {:refresh
   (fn [])
   :get-initial-state
   (fn []
     {:labels []
      :label-gensym (gensym)})
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [creating? server-error validation-errors]} @state]
       [modals/OKCancelForm
        {:header "Create Cluster"
         :dismiss (:dismiss props)
         :ok-button {:text "Create"
                     :onClick #(this :-create-cluster)}
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (when creating? (blocker "Creating cluster..."))
           (style/create-form-label "Name")
           [input/TextField {:data-test-id "cluster-name-input" :ref "clusterNameCreate" :autoFocus true :style {:width "100%"}
                             :defaultValue "" :predicates [(input/nonempty "Cluster name") (input/alphanumeric_- "Cluster name")]}]
           [Collapse
            {:data-test-id "optional-settings"
             :style {:marginLeft -20} :default-hidden? true
             :title [:span {:style {:marginBottom 20 :fontStyle "italic"}} "Optional Settings..."]
             :contents
             (react/create-element
              [:div {}
               (flex/box {}
                 [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
                  [FoundationTooltip {:text (create-inline-form-label "Master Machine Type")
                                      :tooltip "Determines the number of CPUs and memory for the master node."}]]
                 [:div {:style {:width "48%" :marginBottom "1%"}}
                  [FoundationTooltip {:text (create-inline-form-label "Master Disk Size")
                                      :tooltip "Size of the disk on the master node. Minimum size is 100GB."}]])
               [:div {:display "inline-block"}
                (style/create-identity-select {:data-test-id "master-machine-type-select" :ref "masterMachineType"
                                               :style {:width "48%" :marginRight "4%"} :defaultValue "n1-standard-4"}
                  machineTypes)
                [input/TextField {:data-test-id "master-disk-size-input" :ref "masterDiskSize" :autoFocus true
                                  :style {:width "41%"} :defaultValue 500 :min 0 :type "number"}]
                [:span {:style {:marginLeft "2%"}} (create-inline-form-label "GB")]]
               [:div {:style {:marginBottom "1%"}}
                [FoundationTooltip {:text (style/create-form-label "Workers")
                                    :tooltip "Workers can be 0, 2 or more. Google Dataproc does not allow 1 worker."}]]
               [input/TextField {:data-test-id "workers-input" :ref "numberOfWorkers" :autoFocus true
                                 :style {:width "100%"} :defaultValue 0 :min 0 :type "number"}]
               (flex/box {}
                 [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
                  [FoundationTooltip {:text (style/create-form-label "Worker Local SSDs")
                                      :tooltip "The number of local solid state disks for workers. Ignored if Workers is 0."}]]
                 [:div {:style {:width "48%" :marginBottom "1%"}}
                  [FoundationTooltip {:text (style/create-form-label "Preemptible Workers")
                                      :tooltip "Ignored if Workers is 0."}]])
               (flex/box {}
                 [input/TextField {:data-test-id "worker-local-ssds-input" :ref "numberOfWorkerLocalSSDs" :autoFocus true
                                   :style {:width "48%" :marginRight "4%"} :defaultValue 0 :min 0 :type "number"}]
                 [input/TextField {:data-test-id "preemptible-workers-input" :ref "numberOfPreemptibleWorkers"
                                   :autoFocus true :style {:width "48%"} :defaultValue 0 :min 0 :type "number"}])
               (flex/box {}
                 [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
                  [FoundationTooltip {:text (create-inline-form-label "Worker Machine Type")
                                      :tooltip "Determines the number of CPUs and memory for the worker nodes. Ignored if Workers is 0."}]]
                 [:div {:style {:width "48%" :marginBottom "1%"}}
                  [FoundationTooltip {:text (create-inline-form-label "Worker Disk Size")
                                      :tooltip "Size of the disk on each worker node. Minimum size is 100GB. Ignored if Workers is 0."}]])
               [:div {:display "inline-block"}
                (style/create-identity-select {:data-test-id "worker-machine-type-select" :ref "workerMachineType"
                                               :style {:width "48%" :marginRight "4%"} :defaultValue "n1-standard-4"}
                  machineTypes)
                [input/TextField {:data-test-id "worker-disk-size-input" :ref "workerDiskSize" :autoFocus true
                                  :style {:width "41%"} :defaultValue 500 :min 0 :type "number"}]
                [:span {:style {:marginLeft "2%"}} (create-inline-form-label "GB")]]

               [:div {:style {:marginBottom "1%"}}
                [FoundationTooltip {:text (style/create-form-label "Extension URI")
                                    :tooltip "The GCS URI of an archive containing Jupyter notebook extension files.
                                    The archive must be in tar.gz format, must not include a parent directory,
                                    and must have an entry point named 'main'."}]]
               [input/TextField {:data-test-id "extension-uri-input" :ref "extensionURI" :autoFocus true :style {:width "100%"}}]
               [:div {:style {:marginBottom "1%"}}
                [FoundationTooltip {:text (style/create-form-label "Custom Script URI")
                                    :tooltip "The GCS URI of a bash script you wish to run on your cluster before it starts up."}]]
               [input/TextField {:data-test-id "custom-script-uri-input" :ref "userScriptURI" :autoFocus true :style {:width "100%"}}]
               (when (seq (:labels @state))
                 [:div {:key (:label-gensym @state)}
                  (flex/box {}
                    [:span {:style {:width "50%"}} (create-inline-form-label "Key")]
                    [:span {:style {:width "50%" :marginLeft "4%"}} (create-inline-form-label "Value")])
                  (map-indexed (fn [i label]
                                 (flex/box {:style {:marginBottom 10}}
                                   (links/create-internal
                                     {:style {:color (:text-light style/colors)
                                              :marginLeft -20
                                              :minHeight 20 :minWidth 20
                                              }
                                      :href "javascript:;"
                                      :onClick (fn [] (swap! state #(-> % (assoc :label-gensym (gensym))
                                                                        (update :labels utils/delete i))))}
                                     (icons/render-icon {:style {:marginTop "35%"}} :remove))
                                   [input/TextField {:data-test-id (str "key-" i "-input")
                                                     :style {:ref (str "key" i) :marginBottom 0 :width "48%" :marginRight "4%"}
                                                     :defaultValue (first label)
                                                     :onChange #(swap! state update-in [:labels i]
                                                                       assoc 0 (-> % .-target .-value))}]
                                   [input/TextField {:data-test-id (str "value-" i "-input")
                                                     :style {:ref (str "val" i) :marginBottom 0 :width "48%"}
                                                     :defaultValue (last label)
                                                     :onChange #(swap! state update-in [:labels i]
                                                                       assoc 1 (-> % .-target .-value))}]))
                               (:labels @state))])
               [buttons/Button {:text "Add Label" :icon :add-new :style {:marginBottom 10} :data-test-id "add-label-button"
                                :onClick (fn []
                                           (swap! state #(-> %
                                                             (update :labels conj ["" ""])
                                                             (assoc :label-gensym (gensym)))))}]])}]
           [comps/ErrorViewer {:error server-error}]
           (style/create-validation-error-message validation-errors)])}]))
   :-create-cluster
   (fn [{:keys [this state refs props]}]
     (swap! state dissoc :server-error :validation-errors)
     (let [[clusterNameCreate extensionURI userScriptURI & fails] (input/get-and-validate refs "clusterNameCreate" "extensionURI" "userScriptURI")
           payload {:labels (this :-process-labels)}
           machineConfig (this :-process-machine-config)]
       (if fails
         (swap! state assoc :validation-errors fails)
         (do (swap! state assoc :creating? true)
             (endpoints/call-ajax-leo
              {:endpoint (endpoints/create-cluster (get-in props [:workspace-id :namespace]) clusterNameCreate)
               :payload (merge payload
                               {:machineConfig machineConfig}
                               (when-not (string/blank? extensionURI) {:jupyterExtensionUri extensionURI})
                               (when-not (string/blank? userScriptURI) {:jupyterUserScriptUri userScriptURI}))
               :headers ajax/content-type=json
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (swap! state dissoc :creating?)
                          (if success?
                            (do ((:dismiss props)) ((:reload-after-create props))) ;if success, update the table?
                            (swap! state assoc :server-error (get-parsed-response false))))})))))
   :-process-labels
   (fn [{:keys [state]}]
     (let [labelsEmptyRemoved (filter #(not= % ["" ""]) (:labels @state))]
     (zipmap (map (comp keyword first) labelsEmptyRemoved)
             (map last labelsEmptyRemoved))))
   :-process-machine-config
   (fn [{:keys [refs]}]
     (let [getInt #(if (string/blank? %) % (js/parseInt %))
           machineConfig {:numberOfWorkers (getInt (input/get-text refs "numberOfWorkers"))
                          :masterMachineType (.-value (@refs "masterMachineType"))
                          :masterDiskSize (getInt (input/get-text refs "masterDiskSize"))
                          :workerMachineType (.-value (@refs "workerMachineType"))
                          :workerDiskSize (getInt (input/get-text refs "workerDiskSize"))
                          :numberOfWorkerLocalSSDs (getInt (input/get-text refs "numberOfWorkerLocalSSDs"))
                          :numberOfPreemptibleWorkers (getInt (input/get-text refs "numberOfPreemptibleWorkers"))}]
       (apply dissoc
              machineConfig
              (for [[k v] machineConfig :when (or (nil? v) (string/blank? v))] k))))})

(react/defc- ClusterDeleter
  {:render
   (fn [{:keys [state this props]}]
     (let [{:keys [deleting? server-error]} @state
           {:keys [cluster-to-delete]} props]
       [modals/OKCancelForm
        {:header "Delete Cluster"
         :dismiss (:dismiss props)
         :ok-button {:text "Delete"
                     :onClick #(this :-delete-cluster)}
         :content
         (react/create-element
          [:div {}
           (when deleting? (blocker "Deleting cluster..."))
           [:div {} (str "Are you sure you want to delete cluster " cluster-to-delete "?")]
           [comps/ErrorViewer {:error server-error}]])}]))
   :-delete-cluster
   (fn [{:keys [state props]}]
     (let [{:keys [cluster-to-delete]} props]
       (swap! state assoc :deleting? true)
       (swap! state dissoc :server-error)
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/delete-cluster (get-in props [:workspace-id :namespace]) cluster-to-delete)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :deleting?)
                    (if success?
                      (do ((:dismiss props)) ((:reload-after-delete props))) ;if success, update the table?
                      (swap! state assoc :server-error (get-parsed-response false))))})))})

(react/defc- NotebooksTable
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [show-error-dialog? errored-cluster-to-show]} @state
           {:keys [clusters toolbar-items]} props]
       [:div {}
        (when show-error-dialog?
          [modals/OKCancelForm {:header "Cluster Error"
                                :dismiss #(swap! state assoc :show-error-dialog? false)
                                :ok-button {:text "Done"
                                            :onClick #(swap! state assoc :show-error-dialog? false)}
                                :show-cancel? false
                                :content
                                [:div {:style {:width 700}}
                                 [:span {} (str "Cluster " (:clusterName errored-cluster-to-show) " failed with message:")]
                                 [:div {:style {:marginTop "1em" :whiteSpace "pre-wrap" :fontFamily "monospace"
                                                :fontSize "90%" :maxHeight 206
                                                :backgroundColor "#fff" :padding "1em" :borderRadius 8}}
                                  (:errorMessage (first (:errors errored-cluster-to-show)))]]}])
        (when (:show-delete-dialog? @state)
          [ClusterDeleter (assoc props :dismiss #(swap! state dissoc :show-delete-dialog?)
                                       :cluster-to-delete (:cluster-to-delete @state))])
        [Table
         {:data-test-id "spark-clusters-table" :data clusters
          :body {:empty-message "There are no clusters to display."
                 :style table-style/table-light
                 :fixed-column-count 1
                 :column-defaults {"shown" ["delete" "Name" "Status" "Workers" "Create Date"]
                                   "hidden" ["Master Machine Type" "Master Disk Size (GB)" "Worker Machine Type" "Worker Disk Size (GB)" "Worker Local SSDs" "Preemptible Workers"]}
                 :columns
                 [{:id "delete" :initial-width 30
                   :resizable? false :sortable? false :filterable? false :hidden? true
                   :as-text :clusterName :sort-by :clusterName
                   :render
                   (fn [cluster]
                     (if (or (= (:status cluster) "Running") (= (:status cluster) "Error"))
                       (links/create-internal
                         {:data-test-id (str (:clusterName cluster) "-delete-button")
                          :id (:id props)
                          :style {:color (:text-light style/colors)
                                  :minHeight 30 :minWidth 30}
                          :onClick #(swap! state assoc :show-delete-dialog? true :cluster-to-delete (:clusterName cluster))}
                         (icons/render-icon {} :delete))))}

                  {:header "Name" :initial-width 250
                   :as-text :clusterName :sort-by :clusterName :sort-initial :asc
                   :render
                   (fn [cluster]
                     (let [clusterName (:clusterName cluster)]
                       (if (= (:status cluster) "Running")
                         (links/create-external {:data-test-id (str clusterName "-link")
                                                 :href (leo-notebook-url cluster)} clusterName)
                         clusterName)))}
                  {:header "Status" :initial-width 150
                   :as-text :status
                   :render (fn [cluster]
                             (let [clusterNameStatusId (str (:clusterName cluster) "-status")]
                               [:div {:key (when clusters (str (gensym))) ;this makes the spinners sync
                                      :style {:height table-style/table-icon-size}}
                                (icon-for-cluster-status (:status cluster))
                                (if (= (:status cluster) "Error")
                                  (links/create-internal
                                    {:data-test-id clusterNameStatusId
                                     :style {:textDecoration "none" :color (:button-primary style/colors)}
                                     :onClick #(swap! state assoc :show-error-dialog? true :errored-cluster-to-show cluster)}
                                    "View error")
                                  [:span {:data-test-id clusterNameStatusId} (:status cluster)])]))}
                  (table-utils/date-column {:column-data :createdDate :style {}})
                  {:header "Master Machine Type" :initial-width 150
                   :column-data (comp :masterMachineType :machineConfig)}
                  {:header "Master Disk Size (GB)" :initial-width 150
                   :column-data (comp :masterDiskSize :machineConfig)}
                  {:header "Workers" :initial-width 80
                   :column-data (comp :numberOfWorkers :machineConfig)}
                  {:header "Worker Machine Type" :initial-width 150
                   :column-data (comp :workerMachineType :machineConfig)}
                  {:header "Worker Disk Size (GB)" :initial-width 150
                   :column-data (comp :workerDiskSize :machineConfig)}
                  {:header "Worker Local SSDs" :initial-width 130
                   :column-data (comp :numberOfWorkerLocalSSDs :machineConfig)}
                  {:header "Preemptible Workers" :initial-width 150
                   :column-data (comp :numberOfPreemptibleWorkers :machineConfig)}
                  {:header "Labels" :initial-width :auto
                   :column-data #(dissoc (:labels %) :clusterServiceAccount :clusterName :googleProject :googleBucket)
                   :sort-by (comp vec keys)
                   :render
                   (fn [labels]
                     [:div {}
                      (map (fn [label]
                             [:span {:style {:backgroundColor (:background-light style/colors)
                                             :borderTop style/standard-line :borderBottom style/standard-line
                                             :borderRadius 12 :marginRight 4 :padding "0.25rem 0.75rem"}}
                              (if (string/blank? (val label))
                                (name (key label))
                                (str (name (key label)) " | " (val label) "\n\n"))])
                           (into (sorted-map) labels))])}]}
          :toolbar {:get-items (constantly toolbar-items)}}]]))})

(react/defc NotebooksContainer
  {:refresh
   (fn [{:keys [this]}]
     (this :-get-clusters-list)
     (this :-schedule-cookie-refresh))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-response show-create-dialog?]} @state
           {:keys [clusters server-error]} server-response]
       [:div {:display "inline-flex"}
        (when show-create-dialog?
          [ClusterCreator (assoc props :dismiss #(swap! state dissoc :show-create-dialog?)
                                       :reload-after-create #(this :-get-clusters-list))])
        [:div {} [:span {:data-test-id "spark-clusters-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10 :marginLeft 10}} "Spark Clusters"]]
        [:div {:style {:margin 10 :fontSize "88%"}}
         "Launch an interactive analysis environment based on Jupyter notebooks, Spark, and Hail.
          This beta feature is under active development. See documentation " [:a {:href (config/user-notebooks-guide-url) :target "_blank"} "here" icons/external-link-icon]]
        (if server-error
          [comps/ErrorViewer {:data-test-id "notebooks-error" :error server-error}]
          (if clusters
            [NotebooksTable
             (assoc props :toolbar-items [flex/spring [buttons/Button {:data-test-id "create-modal-button"
                                                                       :text "Create Cluster..." :style {:marginRight 7}
                                                                       :onClick #(swap! state assoc :show-create-dialog? true)}]]
                          :clusters clusters
                          :reload-after-delete #(this :-get-clusters-list))]
            [:div {:style {:textAlign "center"}} (spinner "Loading clusters...")]))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-get-clusters-list)
     (this :-schedule-cookie-refresh)
     (.addEventListener js/window "message" (react/method this :-notebook-extension-listener)))

   :component-will-unmount
   (fn [{:keys [this locals]}]
     (swap! locals assoc :dead? true)
     (.removeEventListener js/window "message" (react/method this :-notebook-extension-listener)))

   ; Communicates with the Leo notebook extension.
   ; Use with `react/method` to return a stable binding to the function.
   :-notebook-extension-listener
   (fn [_ e]
     (when (and (= (config/leonardo-url-root) (.-origin e))
                (= "bootstrap-auth.request" (.. e -data -type)))
       (.postMessage (.-source e)
                     (clj->js {:type "bootstrap-auth.response" :body {:googleClientId (config/google-client-id)}})
                     (config/leonardo-url-root))))

   :-schedule-cookie-refresh
   (fn [{:keys [state locals this]}]
     (let [{{:keys [clusters]} :server-response} @state]
       (when-not (:dead? @locals)
         (when (contains-statuses clusters ["Running"])
           (this :-process-running-clusters))
         (js/setTimeout #(this :-schedule-cookie-refresh) 120000))))

   :-process-running-clusters
   (fn [{:keys [state]}]
     (let [{{:keys [clusters]} :server-response} @state
           running-clusters (filter (comp (partial = "Running") :status) clusters)]
       (doseq [cluster running-clusters]
         (ajax/call
          {:url (str (leo-notebook-url cluster) "/setCookie")
           :headers (user/get-bearer-token-header)
           :with-credentials? true
           :cross-domain true
           :on-done (fn [{:keys [success? raw-response]}]
                      (when-not success?
                        (swap! state assoc :server-error raw-response)))}))))

   :-get-clusters-list
   (fn [{:keys [props state locals this]}]
     (when-not (:dead? @locals)
       (endpoints/call-ajax-leo
        {:endpoint endpoints/get-clusters-list
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (let [filtered-clusters (filter #(= (get-in props [:workspace-id :namespace]) (:googleProject %)) (get-parsed-response))]
                        ; Update the state with the current cluster list
                        (when-not (= (:clusters @state) filtered-clusters)
                          (swap! state assoc :server-response {:clusters filtered-clusters}))
                        ; If there are pending clusters, schedule another 'list clusters' call 10 seconds from now.
                        (when (contains-statuses filtered-clusters ["Creating" "Updating" "Deleting"])
                          (js/setTimeout #(this :-get-clusters-list) 10000))
                        ; If there are running clusters, call the /setCookie endpoint immediately.
                        (when (contains-statuses filtered-clusters ["Running"])
                          (this :-process-running-clusters)))
                      (swap! state assoc :server-response {:server-error (get-parsed-response false)})))})))})

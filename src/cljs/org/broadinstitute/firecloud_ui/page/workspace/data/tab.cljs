(ns org.broadinstitute.firecloud-ui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.data.entity-selector :refer [EntitySelector]]
    [org.broadinstitute.firecloud-ui.page.workspace.data.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc DataImporter
  {:render
   (fn [{:keys [state props]}]
     (let [choice? (or (:importing-from-file @state) (:copying-from-workspace @state))]
       [:div {}
        [comps/XButton {:dismiss (:dismiss props)}]
        (when choice?
          [:div {:style {:padding "0.5em"}}
           (style/create-link
             #(swap! state dissoc :importing-from-file :copying-from-workspace)
             (icons/font-icon {:style {:fontSize "70%" :marginRight "1em"}} :angle-left)
             "Back")])
        (when (:importing-from-file @state)
          [:div {:style {:padding "1em"}}
           [import-data/Page (select-keys props [:workspace-id :reload-data-tab])]])
        (when (:copying-from-workspace @state)
          [:div {:style {:padding "1em"}}
           [copy-data-workspaces/Page (select-keys props [:workspace-id :reload-data-tab])]])
        (when-not choice?
          (let [style {:width 240 :margin "auto" :textAlign "center" :cursor "pointer"
                       :backgroundColor (:button-blue style/colors)
                       :color "#fff" :padding "1em" :borderRadius 8}]
            [:div {:style {:padding "2em"}}
             [:div {:onClick #(swap! state assoc :importing-from-file true) :style style}
              "Import from file"]
             [:div {:style {:height "1em"}}]
             [:div {:onClick #(swap! state assoc :copying-from-workspace true)
                    :style style}
              "Copy from another workspace"]]))]))
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))})


(react/defc EntitiesList
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-entity-type (or (:initial-entity-type props) (first (:entity-types props)))})
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (let [attribute-keys (apply
                            union
                            (map #(set (keys (% "attributes")))
                                 (filter #(= (% "entityType") (:selected-entity-type @state))
                                         (:entity-list props))))]
        [table/Table
         {:key (:selected-entity-type @state)
          :empty-message "There are no entities to display."
          :toolbar (fn [built-in]
                     [:div {}
                      [:div {:style {:float "left"}} built-in]
                      (when-let [selected-entity-type (:selected-entity-type @state)]
                        [:a {:style {:textDecoration "none" :float "left" :margin "5px 0 0 1em"}
                             :href (str "/service/api/workspaces/" (:namespace (:workspace-id props)) "/"
                                     (:name (:workspace-id props)) "/" selected-entity-type "/tsv")
                             :target "_blank"}
                         (str "Download '" selected-entity-type "' data")])
                      [:div {:style {:float "right" :paddingRight "2em"}}
                       [comps/Button {:text "Import Data..."
                                      :disabled? (if (:locked? @state) "This workspace is locked")
                                      :onClick (:show-import props)}]]
                      [:div {:style {:float "right" :paddingRight "2em"}}
                       [comps/Button {:text "Delete..."
                                      :disabled? (if (:locked? @state) "This workspace is locked")
                                      :onClick (:show-delete props)}]]
                      (common/clear-both)])
          :columns (concat
                     [{:header "Entity Type" :starting-width 100}
                      {:header "Entity Name" :starting-width 100}]
                     (map (fn [k] {:header k :starting-width 100
                                   :content-renderer
                                   (fn [maybe-uri]
                                     (if (string? maybe-uri)
                                       (if-let [parsed (common/parse-gcs-uri maybe-uri)]
                                         [comps/GCSFilePreviewLink (assoc parsed
                                                                     :gcs-uri maybe-uri)]
                                         maybe-uri)
                                       (table-utils/default-render maybe-uri)))})
                          attribute-keys))
          :filters (mapv (fn [key] {:text key :pred #(= key (% "entityType"))})
                         (if-let [type (:initial-entity-type props)]
                           (cons type (filter #(not= % type) (:entity-types props)))
                           (:entity-types props)))
          :selected-filter-index (.indexOf (to-array (:entity-types props))
                                           (:selected-entity-type @state))
          :on-filter-change #(swap! state assoc :selected-entity-type (nth (:entity-types props) %))
          :data (:entity-list props)
          :->row (fn [m]
                   (concat
                    [(m "entityType")
                     (m "name")]
                    (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))}])])})


(react/defc WorkspaceData
  {:render
   (fn [{:keys [props state refs this]}]
     [:div {:style {:marginTop "1em"}}
      (when (:deleting? @state)
        [comps/Blocker {:banner "Deleting..."}])
      (when (:show-import? @state)
        [comps/Dialog {:dismiss-self #(swap! state dissoc :show-import?)
                       :width "80%"
                       :content
                       (react/create-element
                         [DataImporter {:dismiss #(swap! state dissoc :show-import?)
                                        :workspace-id (:workspace-id props)
                                        :reload-data-tab (fn [entity-type]
                                                           (swap! state dissoc :entity-list :entity-types)
                                                           (react/call :load this entity-type))}])}])
      (when (:show-delete? @state)
        [comps/Dialog
         {:dismiss-self #(swap! state dissoc :show-delete?)
          :width "80%"
          :content
          (react/create-element
            [comps/OKCancelForm
             {:header "Delete Entities"
              :dismiss-self #(swap! state dissoc :show-delete?)
              :content (react/create-element
                         [EntitySelector
                          {:ref "EntitySelector"
                           :left-text "Workspace Entities" :right-text "Will Be Deleted"
                           :entities (:entity-list @state)}])
              :ok-button [comps/Button
                          {:text "Delete"
                           :onClick #(let [selected-entities (react/call :get-selected-entities (@refs "EntitySelector"))
                                           num (count selected-entities)
                                           msg (if (= num 1)
                                                 "Really delete this entity?"
                                                 (str "Really delete these " num " entities?"))]
                                      (if (zero? num)
                                        (js/alert "Please select one or more entities to delete")
                                        (when (js/confirm msg)
                                          (swap! state dissoc :show-delete?)
                                          (react/call :delete this selected-entities))))}]}])}])
      (cond
        (and (:entity-list @state) (contains? @state :locked?))
        [EntitiesList {:entity-list (:entity-list @state)
                       :entity-types (:entity-types @state)
                       :workspace-id (:workspace-id props)
                       :initial-entity-type (:initial-entity-type @state)
                       :show-import #(swap! state assoc :show-import? true)
                       :show-delete #(swap! state assoc :show-delete? true)}]
        (:error @state) (style/create-server-error-message (:error @state))
        :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]])])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [state props]} & [entity-type]]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :locked? (get-in (get-parsed-response) ["workspace" "isLocked"]))
                     (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [entities (get-parsed-response)]
                       (swap! state assoc
                         :entity-list entities
                         :entity-types (distinct (map #(% "entityType") entities))
                         :initial-entity-type entity-type))
                     (swap! state assoc :error status-text)))}))
   :delete
   (fn [{:keys [props state this]} selected-entities]
     (swap! state assoc :deleting? true)
     (let [countdown (atom (count selected-entities))
           errors (atom #{})]
       (doseq [entity selected-entities]
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/delete-entity (:workspace-id props) (entity "entityType") (entity "name"))
            :on-done (fn [{:keys [success? status-text]}]
                       (when-not success?
                         (swap! errors conj status-text))
                       (when (zero? (swap! countdown dec))
                         (swap! state dissoc :deleting? :entity-list)
                         (react/call :load this)
                         (when (pos? (count @errors))
                           (js/alert (apply str (interpose "\n" (cons "Errors:" @errors)))))))}))))})

(defn render [workspace]
  [WorkspaceData {:workspace-id workspace}])
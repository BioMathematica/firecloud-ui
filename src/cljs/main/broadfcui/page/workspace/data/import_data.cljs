(ns broadfcui.page.workspace.data.import-data
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))


(def ^:private preview-limit 4096)

(react/defc Page
  {:get-initial-state
   (fn []
     {:file-input-key (gensym "file-input-")})
   :render
   (fn [{:keys [props state refs this]}]
     (let [{:keys [loading? file-input-key file-contents file upload-result]} @state]
       [:div {:style {:textAlign "center"} :data-test-id "data-upload-container"}
        (when loading?
          (blocker "Uploading file..."))
        ;; This key is changed every time a file is selected causing React to completely replace the
        ;; element. Otherwise, if a user selects the same file (even after having modified it), the
        ;; browser will not fire the onChange event.
        [:input {:key file-input-key
                 :data-test-id "data-upload-input"
                 :type "file" :name "entities" :ref "entities"
                 :style {:display "none"}
                 :onChange (fn [e]
                             (let [file (-> e .-target .-files (aget 0))
                                   reader (js/FileReader.)]
                               (when file
                                 (swap! state dissoc :upload-result)
                                 (set! (.-onload reader)
                                       #(swap! state assoc
                                               :file file
                                               :file-contents (.-result reader)
                                               :file-input-key (gensym "file-input-")))
                                 (.readAsText reader (if (:truncate-preview? props)
                                                       (.slice file 0 preview-limit)
                                                       file)))))}]
        ws-common/PHI-warning
        [buttons/Button {:data-test-id "choose-file-button"
                         :text (if (:upload-result @state) "Choose another file..." "Choose file...")
                         :onClick #(-> (@refs "entities") .click)}]
        (when file-contents
          [:div {:style {:margin "0.5em 2em" :padding "0.5em" :border style/standard-line}}
           (str "Previewing '" (.. (:file @state) -name) "':")
           [:div {:style {:overflow "auto" :maxHeight 200
                          :paddingBottom "0.5em" :textAlign "left"}}
            [:pre {} (clojure.string/replace file-contents #"\r(?!\n)" "\r\n")] ; negative lookahead - match any \r except \r\n
            (when (and
                   (:truncate-preview? props)
                   (> (.-size file) preview-limit))
              [:em {} "(file truncated for preview)"])]])
        (when (and file (not upload-result))
          [buttons/Button {:data-test-id "confirm-upload-metadata-button"
                           :text "Upload"
                           :onClick #(this :do-upload)}])
        (when upload-result
          (if (:success? upload-result)
            (style/create-flexbox
             {:style {:justifyContent "center" :paddingTop "1em"}}
             (icons/render-icon {:style {:fontSize "200%" :color (:state-success style/colors)}} :done)
             [:span {:style {:marginLeft "1em"} :data-test-id "upload-success-message"} "Success!"])
            [:div {:style {:paddingTop "1em"}}
             [comps/ErrorViewer {:error (:error upload-result)}]]))]))
   :do-upload
   (fn [{:keys [props state]}]
     (swap! state assoc :loading? true)
     (if-let [on-upload (:on-upload props)]
       (on-upload (select-keys @state [:file-contents]))
       (let [{:keys [file]} @state
             {:keys [import-type on-data-imported workspace-id]} props
             import-type-is-data? (= "data" import-type)]
         (endpoints/call-ajax-orch
          {:endpoint ((if import-type-is-data?
                        endpoints/import-entities
                        endpoints/import-attributes)
                      workspace-id)
           :raw-data (utils/generate-form-data {(if import-type-is-data? :entities :attributes) file})
           :encType "multipart/form-data"
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :loading? :file :file-contents)
                      (if success?
                        (do
                          (swap! state assoc :upload-result {:success? true})
                          (on-data-imported))
                        (swap! state assoc :upload-result {:success? false :error (get-parsed-response false)})))}))))})

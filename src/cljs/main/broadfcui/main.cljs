(ns broadfcui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.uicomps.modal :as modal]
   [clojure.string :as string]
   [broadfcui.auth :as auth]
   [broadfcui.common :as common]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.notifications :as notifications]
   [broadfcui.common.style :as style]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.top-banner :as top-banner]
   [broadfcui.config :as config]
   [broadfcui.config.loader :as config-loader]
   [broadfcui.footer :as footer]
   [broadfcui.header :as header]
   [broadfcui.injections :as injections]
   [broadfcui.nav :as nav]
   [broadfcui.nih-link-warning :refer [NihLinkWarning]]
   [broadfcui.page.billing.billing-management :as billing-management]
   [broadfcui.page.external-importer :as external-importer]
   [broadfcui.page.groups.groups-management :as group-management]
   [broadfcui.page.library.library-page :as library-page]
   [broadfcui.page.method-repo.method-repo-page :as method-repo]
   [broadfcui.page.method-repo.method.details :as method-details]
   [broadfcui.page.notifications :as billing-notifications]
   [broadfcui.page.profile :as profile-page]
   [broadfcui.page.status :as status-page]
   [broadfcui.page.style-guide :as style-guide]
   [broadfcui.page.workspace.details :as workspace-details]
   [broadfcui.page.workspaces-list :as workspaces]
   [broadfcui.persistence :as persistence]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(injections/setup)


(def ^:private nps-persistence-key "nps-surveymonkey-done")

(defn- init-nav-paths []
  (nav/clear-paths)
  (auth/add-nav-paths)
  (billing-management/add-nav-paths)
  (billing-notifications/add-nav-paths)
  (external-importer/add-nav-paths)
  (group-management/add-nav-paths)
  (library-page/add-nav-paths)
  (method-details/add-nav-paths)
  (method-repo/add-nav-paths)
  (profile-page/add-nav-paths)
  (status-page/add-nav-paths)
  (style-guide/add-nav-paths)
  (workspace-details/add-nav-paths)
  (workspaces/add-nav-paths)
  )

(react/defc- LoggedIn
  {:render
   (fn [{:keys [this props state]}]
     (let [{:keys [component make-props]} props
           path (subs (aget js/window "location" "hash") 1)]
       [:div {}
        [:div {:style {:display "flex" :borderBottom (str "1px solid " (:line-default style/colors))}}
         (when (= :registered (:registration-status @state))
           [header/TopNavBar
            {:items (concat
                     [{:label "Workspaces"
                       :nav-key :workspaces
                       :data-test-id "workspace-nav-link"
                       :is-selected? #(or (empty? path)
                                          (string/starts-with? path "workspaces/"))}
                      {:label "Data Library"
                       :nav-key :library
                       :data-test-id "library-nav-link"
                       :is-selected? #(= path "library")}
                      {:label "Method Repository"
                       :nav-key :method-repo
                       :data-test-id "method-repo-nav-link"
                       :is-selected? #(or (= path "methods")
                                          (string/starts-with? path "methods/"))}])}])
         flex/spring
         [:div {:style {:display "flex" :flexDirection "column" :fontSize "70%" :marginBottom "0.4rem"}}
          [:div {:style {:display "flex" :justifyContent "flex-end"}}
           (dropdown/render-dropdown-menu {:label (icons/render-icon {:style style/secondary-icon-style} :help)
                                           :width 150
                                           :button-style {:height 32 :marginRight "0.5rem" :marginBottom "0.4rem"}
                                           :items [{:href (config/user-guide-url) :target "_blank"
                                                    :text [:span {} "User Guide" icons/external-link-icon]}
                                                   {:href (config/forum-url) :target "_blank"
                                                    :text [:span {} "FireCloud Forum" icons/external-link-icon]}]})
           (header/create-account-dropdown)]]]
        (let [original-destination (aget js/window "location" "hash")
              {:keys [survey-interested?]} @state
              survey-done? (persistence/try-restore
                            {:key nps-persistence-key
                             :initial (constantly false)})
              on-done (fn [fall-through]
                        (when (empty? original-destination)
                          (nav/go-to-path fall-through))
                        (this :-load-registration-status))]
          (case (:registration-status @state)
            nil [:div {:style {:margin "2em 0" :textAlign "center"}}
                 (spinner "Loading user information...")]
            :error [:div {:style {:margin "2em 0"}}
                    (style/create-server-error-message (.-errorMessage this))]
            :not-registered (profile-page/render
                             {:new-registration? true
                              :on-done #(on-done :library)})
            :update-registered (profile-page/render
                                {:update-registration? true
                                 :on-done #(on-done :workspaces)})
            :registered
            (if component
              [:div {}
               [component (make-props)]
               (when-not survey-done?
                 [:div {:style {:position "fixed" :bottom 20 :right 0 :padding 10
                                :borderRadius "0.25rem 0 0 0.25rem"
                                :backgroundColor (:button-primary style/colors) :opacity 0.8
                                :cursor "pointer"}
                        :title "Give feedback"
                        :className "fa-stack"
                        :onClick #(swap! state assoc :survey-interested? true)}
                  (icons/render-icon {:className "fa-stack-2x" :style {:color "white"}} :comment)
                  (icons/render-icon {:className "fa-stack-1x" :style {:color (:button-primary style/colors)}} :add-new)
                  (when survey-interested?
                    [ScriptLoader {:path "npssurvey.js"
                                   :allow-cache? true
                                   :on-error identity ;; we fail silently if we can't load the survey
                                   :on-load #(do
                                               (swap! state dissoc :survey-interested?)
                                               (persistence/save-value nps-persistence-key true))}])])]
              [:h2 {} "Page not found."])))]))
   :component-did-mount
   (fn [{:keys [this state]}]
     (when (nil? (:registration-status @state))
       (this :-load-registration-status)))
   :-load-registration-status
   (fn [{:keys [this state]}]
     (user/reload-profile
      (fn [{:keys [success? status-text get-parsed-response]}]
        (let [parsed-values (when success? (common/parse-profile (get-parsed-response)))]
          (cond
            (and success? (>= (int (:isRegistrationComplete parsed-values)) 3))
            (swap! state assoc :registration-status :registered)
            (and success? (some? (:isRegistrationComplete parsed-values))) ; partial profile case
            (swap! state assoc :registration-status :update-registered)
            success? ; unregistered case
            (swap! state assoc :registration-status :not-registered)
            :else
            (do
              (set! (.-errorMessage this) status-text)
              (swap! state assoc :registration-status :error)))))))})

(defn- render-js-exception [e dismiss]
  [modals/OKCancelForm
   {:header [:span {} (icons/render-icon {:style {:color (:warning-state style/colors)
                                                  :marginRight "1rem"}}
                                         :warning)
             "Something Went Wrong"]
    :content [:div {:style {:width 800}}
              "A JavaScript error occurred; please try reloading the page. If the error persists, please report it to our "
              (links/create-external {:href (config/forum-url)} "forum")
              " for help. Details of the error message are below."
              [:div {:style {:fontFamily "monospace" :whiteSpace "pre" :overflow "auto"
                             :backgroundColor "black" :color "white"
                             :padding "0.5rem" :marginTop "0.5rem" :borderRadius "0.3rem"}}
               [:div {:style {:fontWeight "bold"}} "Error: "]
               (aget e "message")
               [:div {:style {:fontWeight "bold" :paddingTop "0.5rem"}} "Source: "]
               (aget e "filename")]]
    :show-cancel? false :dismiss dismiss :ok-button "OK"}])

(react/defc- App
  {:handle-hash-change
   (fn [{:keys [state]}]
     (let [window-hash (aget js/window "location" "hash")]
       (when-not (nav/execute-redirects window-hash)
         (swap! state assoc :window-hash window-hash))))
   :get-initial-state
   (fn []
     {:user-status #{}})
   :component-will-mount
   (fn [{:keys [this state]}]
     (init-nav-paths)
     (this :handle-hash-change)
     (set! (.-forceSignedIn js/window)
           (auth/force-signed-in {:on-sign-in #(swap! state update :user-status conj :signed-in)
                                  :on-sign-out #(swap! state update :user-status disj :signed-in)
                                  :on-error #(swap! state assoc :force-sign-in-error %)})))
   :render
   (fn [{:keys [state]}]
     (let [{:keys [auth2 user-status window-hash config-loaded?]} @state
           {:keys [component make-props public?]} (nav/find-path-handler window-hash)
           sign-in-hidden? (or (nil? component)
                               public?
                               (contains? (:user-status @state) :signed-in))]
       [:div {}
        (when (contains? user-status :signed-in)
          [notifications/TrialAlertContainer])
        (when-let [error (:force-sign-in-error @state)]
          (modals/render-error {:header (str "Error validating access token")
                                :text (auth/render-forced-sign-in-error error)
                                :dismiss #(swap! state dissoc :force-sign-in-error)}))
        (when (and (contains? user-status :signed-in)
                   (not (or (nav/is-current-path? :profile)
                            (nav/is-current-path? :status))))
          [NihLinkWarning])
        [top-banner/Container]
        (when config-loaded?
          [notifications/ServiceAlertContainer])
        (when (and (contains? user-status :signed-in) (contains? user-status :refresh-token-saved))
          [auth/RefreshCredentials {:auth2 auth2}])
        [:div {:style {:position "relative"}}
         [:div {:style {:backgroundColor "white" :padding 20}}
          (when-not (contains? user-status :signed-in)
            (style/render-text-logo))
          [:div {}
           [auth/LoggedOut {:spinner-text (cond (not config-loaded?) "Loading config..."
                                                (not auth2) "Loading auth...")
                            :auth2 auth2 :hidden? sign-in-hidden?
                            :on-change (fn [signed-in? token-saved?]
                                         (swap! state update :user-status
                                                #(-> %
                                                     ((if signed-in? conj disj)
                                                      :signed-in)
                                                     ((if token-saved? conj disj)
                                                      :refresh-token-saved))))}]
           (when (and config-loaded? (not auth2))
             [auth/GoogleAuthLibLoader {:on-loaded #(swap! state assoc :auth2 %)}])

           (cond
             (not config-loaded?)
             [config-loader/Component
              {:on-success (fn []
                             (swap! state assoc :config-loaded? true)
                             (when (config/debug?)
                               (.addEventListener
                                js/window "error"
                                (fn [e]
                                  (swap! state assoc :showing-js-error-dialog? true :js-error e)))))}]
             (and (not (contains? user-status :signed-in)) (nil? component))
             [:h2 {} "Page not found."]
             public?
             [component (make-props)]
             (contains? user-status :signed-in)
             (cond
               (not (contains? user-status :go))
               [auth/UserStatus {:on-success #(swap! state update :user-status conj :go)}]
               :else [LoggedIn {:component component :make-props make-props}]))]]
         (footer/render-footer)
         (when (:showing-system-down-banner? @state)
           (let [title (if (:maintenance-mode? @state) "Maintenance Mode"
                                                       "Server Unavailable")
                 msg (if (:maintenance-mode? @state) "FireCloud is currently undergoing planned maintenance. We should be back online shortly."
                                                     "FireCloud service is temporarily unavailable.")]
             (notifications/render-alert {:title title
                                          :message msg
                                          :link (config/status-url)
                                          :cleared? false
                                          :link-title "System Status"
                                          :severity :info} nil)))
         (when (:showing-js-error-dialog? @state)
           (render-js-exception
            (:js-error @state)
            #(swap! state dissoc :showing-js-error-dialog? :js-error)))
         ;; As low as possible on the page so it will be the frontmost component when displayed.
         [modal/Container {:z-index style/modals-z-index}]]]))
   :component-did-mount
   (fn [{:keys [state this refs locals]}]
     ;; Show system status banner at the first 503, hide it at the first success
     (add-watch
      ajax/server-down? :server-watcher
      (fn [_ _ _ down-now?]
        (swap! state assoc :showing-system-down-banner? down-now? :maintenance-mode? false)))
     (add-watch
      ajax/maintenance-mode? :server-watcher
      (fn [_ _ _ maintenance-now?]
        (swap! state assoc :showing-system-down-banner? maintenance-now? :maintenance-mode? true)))
     (swap! locals assoc :hash-change-listener (partial this :handle-hash-change))
     (.addEventListener js/window "hashchange" (:hash-change-listener @locals))
     (aset js/window "testJsException"
           (fn [] (js/setTimeout #(throw (js/Error. "You told me to do this.")) 100) nil)))
   :component-will-receive-props
   (fn []
     (init-nav-paths))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "hashchange" (:hash-change-listener @locals))
     (remove-watch ajax/server-down? :server-watcher)
     (remove-watch ajax/maintenance-mode? :server-watcher))})


(defn render-application []
  (react/render (react/create-element App) (utils/get-app-root-element)))


(render-application)

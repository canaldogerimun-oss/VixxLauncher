package net.kdt.pojavlaunch.fragments;

import static com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.END;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.movtery.anim.AnimPlayer;
import com.movtery.anim.animations.Animations;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.FragmentLauncherBinding;
import com.movtery.zalithlauncher.event.single.LaunchGameEvent;
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent;
import com.movtery.zalithlauncher.feature.version.Version;
import com.movtery.zalithlauncher.feature.version.VersionInfo;
import com.movtery.zalithlauncher.feature.version.VersionsManager;
import com.movtery.zalithlauncher.feature.version.utils.VersionIconUtils;
import com.movtery.zalithlauncher.task.TaskExecutors;
import com.movtery.zalithlauncher.ui.fragment.AccountFragment;
import com.movtery.zalithlauncher.ui.fragment.ControlButtonFragment;
import com.movtery.zalithlauncher.ui.fragment.FilesFragment;
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim;
import com.movtery.zalithlauncher.ui.fragment.ModsFragment;
import com.movtery.zalithlauncher.ui.fragment.SettingsFragment;
import com.movtery.zalithlauncher.ui.fragment.VixxPerformanceFragment;
import com.movtery.zalithlauncher.ui.fragment.VixxSkinStudioFragment;
import com.movtery.zalithlauncher.ui.fragment.VersionManagerFragment;
import com.movtery.zalithlauncher.ui.fragment.VersionsListFragment;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.path.PathManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;

public class MainMenuFragment extends FragmentWithAnim {
    public static final String TAG = "MainMenuFragment";
    private FragmentLauncherBinding binding;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLauncherBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Primary navigation: every implemented item opens the existing functional screen.
        binding.aboutButton.setOnClickListener(v -> {
            // Already on the dashboard. This is intentionally a no-op.
        });
        binding.navPlayButton.setOnClickListener(v -> openVersions());
        binding.navModsButton.setOnClickListener(v -> openMods());
        binding.navSkinsButton.setOnClickListener(v -> openSkins());
        binding.navAccountsButton.setOnClickListener(v -> openAccounts());
        binding.navPerformanceButton.setOnClickListener(v ->
                ZHTools.swapFragmentWithAnim(this, VixxPerformanceFragment.class, VixxPerformanceFragment.TAG, null));
        binding.navCommunityButton.setOnClickListener(v -> showPlannedFeature("Comunidade"));
        binding.navSettingsButton.setOnClickListener(v -> openSettings());

        // Existing Zalith control editor is kept accessible from the VIXX navigation rail.
        binding.customControlButton.setOnClickListener(v ->
                ZHTools.swapFragmentWithAnim(this, ControlButtonFragment.class, ControlButtonFragment.TAG, null));

        // Quick actions in the center of the dashboard.
        binding.quickPlayButton.setOnClickListener(v -> launchGame());
        binding.quickModsButton.setOnClickListener(v -> openMods());
        binding.quickSkinsButton.setOnClickListener(v -> openSkins());
        binding.quickAccountsButton.setOnClickListener(v -> openAccounts());

        // Version selection and version-specific settings.
        binding.version.setOnClickListener(v -> {
            if (!isTaskRunning()) {
                openVersions();
            } else {
                Toast.makeText(requireContext(), R.string.version_manager_task_in_progress, Toast.LENGTH_SHORT).show();
            }
        });
        binding.managerProfileButton.setOnClickListener(v -> {
            if (!isTaskRunning()) {
                ZHTools.swapFragmentWithAnim(this, VersionManagerFragment.class, VersionManagerFragment.TAG, null);
            } else {
                Toast.makeText(requireContext(), R.string.version_manager_task_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        binding.openMainDirButton.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(FilesFragment.BUNDLE_LIST_PATH, PathManager.DIR_GAME_HOME);
            ZHTools.swapFragmentWithAnim(this, FilesFragment.class, FilesFragment.TAG, bundle);
        });
        binding.installJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        binding.installJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
        binding.shareLogsButton.setOnClickListener(v -> ZHTools.shareLogs(requireActivity()));
        binding.playButton.setOnClickListener(v -> launchGame());

        binding.versionName.setSelected(true);
        binding.versionInfo.setSelected(true);
        binding.pathText.setText(PathManager.DIR_GAME_HOME);
        refreshCurrentVersion();
    }

    private void openVersions() {
        ZHTools.swapFragmentWithAnim(this, VersionsListFragment.class, VersionsListFragment.TAG, null);
    }


    private void openSkins() {
        ZHTools.swapFragmentWithAnim(this, VixxSkinStudioFragment.class, VixxSkinStudioFragment.TAG, null);
    }

    private void openAccounts() {
        ZHTools.swapFragmentWithAnim(this, AccountFragment.class, AccountFragment.TAG, null);
    }

    private void openSettings() {
        ZHTools.swapFragmentWithAnim(this, SettingsFragment.class, SettingsFragment.TAG, null);
    }

    private void openMods() {
        File modsDirectory;
        Version version = VersionsManager.INSTANCE.getCurrentVersion();
        if (version != null) {
            modsDirectory = new File(version.getGameDir(), "mods");
        } else {
            modsDirectory = new File(PathManager.DIR_GAME_HOME, "mods");
        }
        if (!modsDirectory.exists()) modsDirectory.mkdirs();

        Bundle bundle = new Bundle();
        bundle.putString(ModsFragment.BUNDLE_ROOT_PATH, modsDirectory.getAbsolutePath());
        ZHTools.swapFragmentWithAnim(this, ModsFragment.class, ModsFragment.TAG, bundle);
    }

    private void launchGame() {
        EventBus.getDefault().post(new LaunchGameEvent());
    }

    private void showPlannedFeature(String featureName) {
        Toast.makeText(
                requireContext(),
                featureName + " terá uma tela VIXX própria em uma próxima etapa. As funções atuais do launcher foram preservadas.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void refreshCurrentVersion() {
        Version version = VersionsManager.INSTANCE.getCurrentVersion();

        if (version != null) {
            binding.versionName.setText(version.getVersionName());
            binding.selectedVersionName.setText(version.getVersionName());

            VersionInfo versionInfo = version.getVersionInfo();
            if (versionInfo != null) {
                String info = versionInfo.getInfoString();
                binding.versionInfo.setText(info);
                binding.versionInfo.setVisibility(View.VISIBLE);
                binding.selectedVersionInfo.setText(info);
            } else {
                binding.versionInfo.setText("Versão selecionada");
                binding.versionInfo.setVisibility(View.GONE);
                binding.selectedVersionInfo.setText("Selecionada");
            }

            new VersionIconUtils(version).start(binding.versionIcon);
            new VersionIconUtils(version).start(binding.sideVersionIcon);
            binding.managerProfileButton.setVisibility(View.VISIBLE);
        } else {
            binding.versionName.setText(R.string.version_no_versions);
            binding.selectedVersionName.setText(R.string.version_no_versions);
            binding.versionInfo.setVisibility(View.GONE);
            binding.selectedVersionInfo.setText("Selecione uma instalação");
            binding.versionIcon.setImageResource(R.drawable.ic_minecraft);
            binding.sideVersionIcon.setImageResource(R.drawable.ic_minecraft);
            binding.managerProfileButton.setVisibility(View.GONE);
        }
    }

    @Subscribe
    public void event(RefreshVersionsEvent event) {
        if (event.getMode() == END) {
            TaskExecutors.runInUIThread(this::refreshCurrentVersion);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0) {
            Tools.installMod(requireActivity(), isCustomArgs);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void slideIn(AnimPlayer animPlayer) {
        animPlayer.apply(new AnimPlayer.Entry(binding.launcherMenu, Animations.BounceInDown))
                .apply(new AnimPlayer.Entry(binding.playLayout, Animations.BounceInLeft))
                .apply(new AnimPlayer.Entry(binding.playButtonsLayout, Animations.BounceEnlarge));
    }

    @Override
    public void slideOut(AnimPlayer animPlayer) {
        animPlayer.apply(new AnimPlayer.Entry(binding.launcherMenu, Animations.FadeOutUp))
                .apply(new AnimPlayer.Entry(binding.playLayout, Animations.FadeOutRight))
                .apply(new AnimPlayer.Entry(binding.playButtonsLayout, Animations.BounceShrink));
    }
}

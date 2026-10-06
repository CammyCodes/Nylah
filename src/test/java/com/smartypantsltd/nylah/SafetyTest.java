package com.smartypantsltd.nylah;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promises that keep her invisible to the server, checked rather than trusted:
 * she is never pickable, clicks on her are decided correctly, she appears only out
 * of view, and nothing in the mod can send anything to a server.
 */
class SafetyTest {

    // --- strokes -------------------------------------------------------------------

    @Test
    void strokeTruthTable() {
        // present, screen, hit, herDist, blockDist, handEmpty
        assertEquals(Gesture.Outcome.STROKE, Gesture.decide(true, false, true, 2, 10, true));
        assertEquals(Gesture.Outcome.PASS, Gesture.decide(false, false, true, 2, 10, true), "not here");
        assertEquals(Gesture.Outcome.PASS, Gesture.decide(true, true, true, 2, 10, true), "a screen is open");
        assertEquals(Gesture.Outcome.PASS, Gesture.decide(true, false, false, 2, 10, true), "not aimed at her");
        assertEquals(Gesture.Outcome.PASS, Gesture.decide(true, false, true, 3, 1.5, true), "a block is in front");
        assertEquals(Gesture.Outcome.PASS, Gesture.decide(true, false, true, 2, 10, false), "holding something");
    }

    @Test
    void holdingAnythingAlwaysPassesTheClickOn() {
        for (boolean present : new boolean[] {true, false}) {
            for (boolean screen : new boolean[] {true, false}) {
                for (boolean hit : new boolean[] {true, false}) {
                    for (double her = 0.2; her < 5; her += 0.4) {
                        for (double block = 0.2; block < 6; block += 0.4) {
                            assertEquals(Gesture.Outcome.PASS, Gesture.decide(present, screen, hit, her, block, false));
                        }
                    }
                }
            }
        }
    }

    @Test
    void neverAStrokeWhenNotAimedAtHer() {
        for (boolean hand : new boolean[] {true, false}) {
            for (double her = 0.2; her < 5; her += 0.4) {
                for (double block = 0.2; block < 6; block += 0.4) {
                    assertEquals(Gesture.Outcome.PASS, Gesture.decide(true, false, false, her, block, hand));
                }
            }
        }
    }

    @Test
    void headIsFrontTopBodyIsTheRest() {
        assertEquals(Gesture.Region.HEAD, Gesture.region(0.8, 0, 1, 0.2));
        assertEquals(Gesture.Region.BODY, Gesture.region(0.8, 0, 1, -0.2));
        assertEquals(Gesture.Region.BODY, Gesture.region(0.2, 0, 1, 0.2));
    }

    // --- appearing out of view ----------------------------------------------------------

    @Test
    void spawnCandidatesStartBehindTheCamera() {
        for (float yaw = -180; yaw < 180; yaw += 15) {
            double[] first = SpawnSpots.candidates(yaw, new double[] {7.5}).get(0);
            assertTrue(SpawnSpots.outOfView(yaw, first[0], first[1], 70), "yaw " + yaw);
            double ang = Math.toRadians(yaw);
            assertFalse(SpawnSpots.outOfView(yaw, -Math.sin(ang) * 6, Math.cos(ang) * 6, 70), "straight ahead is in view");
        }
    }

    // --- nothing reaches the server ------------------------------------------------------

    /** Classes and members the mod must never reference: each is a way to tell the server something. */
    private static final List<String> FORBIDDEN_OWNERS = List.of(
            "net/minecraft/network/protocol/game/ServerboundInteractPacket",
            "net/minecraft/network/protocol/game/ServerboundChatPacket",
            "net/minecraft/network/protocol/game/ServerboundChatCommandPacket",
            "net/minecraft/network/protocol/game/ServerboundContainerClickPacket",
            "net/minecraft/network/Connection");

    private static final List<String> FORBIDDEN_CALLS = List.of(
            "net/minecraft/client/multiplayer/MultiPlayerGameMode.attack",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode.interact",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode.interactAt",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode.useItem",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode.useItemOn",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode.handleContainerInput",
            "net/minecraft/client/multiplayer/ClientPacketListener.send",
            "net/minecraft/client/multiplayer/ClientPacketListener.sendChat",
            "net/minecraft/client/multiplayer/ClientPacketListener.sendCommand",
            "net/minecraft/client/multiplayer/ClientPacketListener.sendUnattendedCommand",
            "net/minecraft/client/player/LocalPlayer.closeContainer");

    @Test
    void nothingInTheModCanTalkToTheServer() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path cls : classes()) {
            try (InputStream in = Files.newInputStream(cls)) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int op, String owner, String mname, String mdesc, boolean itf) {
                                if (FORBIDDEN_OWNERS.contains(owner) || FORBIDDEN_CALLS.contains(owner + "." + mname)) {
                                    problems.add(cls.getFileName() + " calls " + owner + "." + mname);
                                }
                            }

                            @Override
                            public void visitTypeInsn(int op, String type) {
                                if (FORBIDDEN_OWNERS.contains(type)) {
                                    problems.add(cls.getFileName() + " uses " + type);
                                }
                            }

                            @Override
                            public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
                                if (op == Opcodes.PUTFIELD && fname.equals("containerMenu")) {
                                    problems.add(cls.getFileName() + " assigns containerMenu");
                                }
                            }
                        };
                    }
                }, 0);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void sheIsNeverPickable() throws IOException {
        Path cat = classes().stream().filter(p -> p.getFileName().toString().equals("NylahCat.class")).findFirst().orElseThrow();
        List<Integer> ops = new ArrayList<>();
        boolean[] found = {false};
        try (InputStream in = Files.newInputStream(cat)) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if (!name.equals("isPickable") || !desc.equals("()Z")) {
                        return null;
                    }
                    found[0] = true;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInsn(int op) {
                            ops.add(op);
                        }

                        @Override
                        public void visitJumpInsn(int op, org.objectweb.asm.Label l) {
                            ops.add(op);
                        }

                        @Override
                        public void visitMethodInsn(int op, String o, String n, String d, boolean i) {
                            ops.add(op);
                        }
                    };
                }
            }, 0);
        }
        assertTrue(found[0], "NylahCat must override isPickable");
        assertEquals(List.of(Opcodes.ICONST_0, Opcodes.IRETURN), ops, "isPickable must be exactly 'return false'");
    }

    private static List<Path> classes() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String dir : System.getProperty("nylah.classes").split(java.io.File.pathSeparator)) {
            Path root = Path.of(dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(p -> p.toString().endsWith(".class")).forEach(out::add);
            }
        }
        assertFalse(out.isEmpty(), "no compiled classes found to audit");
        return out;
    }
}

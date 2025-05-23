/*
 * Copyright (C) 2017-2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.util;

import org.jivesoftware.util.Version.ReleaseStatus;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class VersionTest {

    @Test
    public void testVersionWithInitializingConstructor() {
        Version test = new Version(3, 2, 1, ReleaseStatus.Beta, 4);
        
        assertEquals(3, test.getMajor());
        assertEquals(2, test.getMinor());
        assertEquals(1, test.getMicro());
        
        assertEquals(ReleaseStatus.Beta, test.getStatus());
        assertEquals(4, test.getStatusVersion());
        
        assertEquals("3.2.1 Beta 4", test.getVersionString());
    }

    @Test
    public void testVersionWithRegularStringConstructor() {
        Version test = new Version("1.2.3 Beta 3");
        
        assertEquals(1, test.getMajor());
        assertEquals(2, test.getMinor());
        assertEquals(3, test.getMicro());

        assertEquals(ReleaseStatus.Beta, test.getStatus());
        assertEquals(3, test.getStatusVersion());
        
        assertEquals("1.2.3 Beta 3", test.getVersionString());
    }

    @Test
    public void testVersionWithRegularStringConstructorB() {
        Version test = new Version("1.2.3 Release 3");

        assertEquals(1, test.getMajor());
        assertEquals(2, test.getMinor());
        assertEquals(3, test.getMicro());

        assertEquals(ReleaseStatus.Release, test.getStatus());
        assertEquals(3, test.getStatusVersion());

        assertEquals("1.2.3 Release 3", test.getVersionString());
    }

    @Test
    public void testVersionWithNullStringConstructor() {
        Version test = new Version(null);
        
        assertEquals(0, test.getMajor());
        assertEquals(0, test.getMinor());
        assertEquals(0, test.getMicro());
        
        assertEquals(ReleaseStatus.Release, test.getStatus());
        assertEquals(-1, test.getStatusVersion());
        
        assertEquals("0.0.0", test.getVersionString());
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    public void testVersionComparisons() {
        
        Version test123 = new Version("1.2.3");
        Version test321 = new Version("3.2.1");
        Version test322 = new Version("3.2.2");
        Version test333 = new Version("3.3.3");
        Version test300 = new Version("3.0.0");
        Version test3100 = new Version("3.10.0");
        Version test29999 = new Version("2.999.999");
        Version test3100Alpha = new Version("3.10.0 Alpha");
        Version test3100Beta = new Version("3.10.0 Beta");
        Version test3100Beta1 = new Version("3.10.0 Beta 1");
        Version test3100Beta2 = new Version("3.10.0 Beta 2");
        assertEquals(-1, test123.compareTo(test321));
        assertEquals(0, test123.compareTo(test123));
        assertEquals(1, test321.compareTo(test123));
        
        assertTrue(test322.isNewerThan(test321));
        assertFalse(test322.isNewerThan(test333));
        assertFalse(test300.isNewerThan(test321));
        assertTrue(test3100.isNewerThan(test333));
        assertTrue(test3100.isNewerThan(test29999));
        assertTrue(test300.isNewerThan(test29999));
        assertTrue(test3100Beta.isNewerThan(test3100Alpha));
        assertTrue(test3100Beta2.isNewerThan(test3100Beta1));
    }

    @Test
    public void testVersionEquals() {
        Version version1 = new Version(3, 11, 0, Version.ReleaseStatus.Alpha, -1);
        Version version2 = new Version(3, 11, 0, Version.ReleaseStatus.Alpha, -1);
        assertEquals(version1, version2);
        assertEquals((version1.compareTo(version2) == 0), version1.equals(version2));
    }

    @Test
    public void willVersionAThreeDigitSnapshot() {
        final String versionString = "1.2.3-SNAPSHOT";
        Version test = new Version(versionString);

        assertThat(test.getMajor(), is(1));
        assertThat(test.getMinor(), is(2));
        assertThat(test.getMicro(), is(3));
        assertThat(test.getStatusVersion(), is(-1));
        assertThat(test.getStatus(), is(ReleaseStatus.Snapshot));
        assertThat(test.getVersionString(),is(versionString));
    }

    @Test
    public void willVersionAFourDigitSnapshot() {
        final String versionString = "1.2.3.4-snapshot";
        Version test = new Version(versionString);

        assertThat(test.getMajor(), is(1));
        assertThat(test.getMinor(), is(2));
        assertThat(test.getMicro(), is(3));
        assertThat(test.getStatusVersion(), is(4));
        assertThat(test.getStatus(), is(ReleaseStatus.Snapshot));
        assertThat(test.getVersionString(),is(versionString.toUpperCase()));

    }

    @Test
    public void anAlphaVersionIgnoringTheReleaseStatusIsNotNewerThanTheReleaseVersion() {

        final Version releaseVersion = new Version("4.3.0");
        final Version alphaVersion = new Version("4.3.0 alpha");

        assertThat(releaseVersion.isNewerThan(alphaVersion), is(true));
        assertThat(releaseVersion.isNewerThan(alphaVersion.ignoringReleaseStatus()), is(false));
    }

    @Test
    public void willVersionAFourDigitRelease() {

        final String versionString = "1.2.3.4";
        final Version test = new Version(versionString);

        assertThat(test.getMajor(), is(1));
        assertThat(test.getMinor(), is(2));
        assertThat(test.getMicro(), is(3));
        assertThat(test.getStatusVersion(), is(4));
        assertThat(test.getStatus(), is(ReleaseStatus.Release));
        assertThat(test.getVersionString(),is("1.2.3 Release 4"));
    }

    @Test // OF-2941
    public void testSnapshotCompareToRelease() {
        final Version snapshotVersion = new Version(10,1,7, ReleaseStatus.Snapshot, 3);
        final Version olderReleaseVersion = new Version(10,1,7, ReleaseStatus.Release, 2);

        assertTrue(snapshotVersion.isNewerThan(olderReleaseVersion));
    }

    @Test // OF-2941
    public void testAlphaCompareToRelease() {
        final Version alphaVersion = new Version(10,1,7, ReleaseStatus.Alpha, 3);
        final Version newerReleaseVersion = new Version(10,1,7, ReleaseStatus.Release, 2);

        assertTrue(newerReleaseVersion.isNewerThan(alphaVersion));
    }

    @Test // OF-2941
    public void testBetaCompareToRelease() {
        final Version betaVersion = new Version(10,1,7, ReleaseStatus.Beta, 3);
        final Version newerReleaseVersion = new Version(10,1,7, ReleaseStatus.Release, 2);

        assertTrue(newerReleaseVersion.isNewerThan(betaVersion));
    }

    @Test // OF-2941
    public void testReleaseCandidateCompareToRelease() {
        final Version releaseCandidateVersion = new Version(10,1,7, ReleaseStatus.Release_Candidate, 3);
        final Version newerReleaseVersion = new Version(10,1,7, ReleaseStatus.Release, 2);

        assertTrue(newerReleaseVersion.isNewerThan(releaseCandidateVersion));
    }

    @Test // OF-2941
    public void testReleaseCompareToRelease() {
        final Version releaseVersion = new Version(10,1,7, ReleaseStatus.Release, 3);
        final Version olderReleaseVersion = new Version(10,1,7, ReleaseStatus.Release, 2);

        assertTrue(releaseVersion.isNewerThan(olderReleaseVersion));
    }
}
